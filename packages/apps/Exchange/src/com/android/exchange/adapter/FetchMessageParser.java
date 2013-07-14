/*
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *     Neither the name of Code Aurora Forum, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.exchange.adapter;

import android.content.ContentResolver;
import android.content.ContentValues;

import com.android.emailcommon.internet.MimeMessage;
import com.android.emailcommon.internet.MimeUtility;
import com.android.emailcommon.mail.MessagingException;
import com.android.emailcommon.mail.Part;
import com.android.emailcommon.provider.EmailContent.Body;
import com.android.emailcommon.provider.EmailContent.BodyColumns;
import com.android.emailcommon.provider.EmailContent.Message;
import com.android.emailcommon.provider.EmailContent.MessageColumns;
import com.android.emailcommon.utility.ConversionUtilities;
import com.android.exchange.Eas;
import com.android.exchange.EasSyncService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class FetchMessageParser extends Parser {
    /**
     * Response:
     * <?xml version="1.0" encoding="utf-8"?>
     * <ItemOperations xmlns:airsync="AirSync:" xmlns:email="Email:" xmlns:email2="Email2:" xmlns="ItemOperations:">
     *     <Status>1</Status>
     *     <Response>
     *         <Fetch>
     *             <Status>1</Status>    // uses the same values as the parent ItemOperations response Status element
     *             <airsync:CollectionId>collectionId</airsync:CollectionId>
     *             <airsync:ServerId>serverId</airsync:ServerId>
     *             <airsync:Class>Email</airsync:Class>
     *             <Properties>
     *                 ...
     *             </Properties>
     *         </Fetch>
     *     </Response>
     * </ItemOperations>
     */

    private final EasSyncService mService;
    private Message mMessage;
    private int mStatusCode = 0;
    private String mBodyType;

    public static final int STATUS_CODE_SUCCESS = 1;

    public FetchMessageParser(InputStream in, EasSyncService service, Message msg) throws IOException {
        super(in);
        mService = service;
        mMessage = msg;
    }

    public int getStatusCode() {
        return mStatusCode;
    }

    // commit the body data to database.
    public void commit(ContentResolver contentResolver) {
        mService.userLog("Fetched message body successfully for " + mMessage.mId);

        // update the body data
        ContentValues cv = new ContentValues();
        if (mBodyType.equals(Eas.BODY_PREFERENCE_HTML)) {
            cv.put(BodyColumns.HTML_CONTENT, mMessage.mHtml);
        } else {
            cv.put(BodyColumns.TEXT_CONTENT, mMessage.mText);
        }
        int res = contentResolver.update(Body.CONTENT_URI, cv, BodyColumns.MESSAGE_KEY + "=" + mMessage.mId, null);
        mService.userLog("update the body content, success number : " + res);

        // update the loaded flag to database.
        cv.clear();
        cv.put(MessageColumns.FLAG_LOADED, Message.FLAG_LOADED_COMPLETE);
        res = contentResolver.update(Message.CONTENT_URI, cv, MessageColumns.ID + "=" + mMessage.mId, null);
        mService.userLog("update the message content, success number : " + res);
    }

    public void parseBody() throws IOException {
        mBodyType = Eas.BODY_PREFERENCE_TEXT;
        String body = "";
        while (nextTag(Tags.EMAIL_BODY) != END) {
            switch (tag) {
                case Tags.BASE_TYPE:
                    mBodyType = getValue();
                    break;
                case Tags.BASE_DATA:
                    body = getValue();
                    break;
                case Tags.BASE_TRUNCATED:
                    mService.userLog("Shouldn't be there, the request is fetching the entire mail.");
                    break;
                default:
                    skipTag();
            }
        }
        // We always ask for TEXT or HTML; there's no third option
        if (mBodyType.equals(Eas.BODY_PREFERENCE_HTML)) {
            mMessage.mHtml = body;
        } else {
            mMessage.mText = body;
        }
    }

    public void parseMIMEBody(String mimeData) throws IOException {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(mimeData.getBytes());
            // The constructor parses the message
            MimeMessage mimeMessage = new MimeMessage(in);
            // Now process body parts & attachments
            ArrayList<Part> viewables = new ArrayList<Part>();
            // We'll ignore the attachments, as we'll get them directly from EAS
            ArrayList<Part> attachments = new ArrayList<Part>();
            MimeUtility.collectParts(mimeMessage, viewables, attachments);
            Body tempBody = new Body();
            // updateBodyFields fills in the content fields of the Body
            ConversionUtilities.updateBodyFields(tempBody, mMessage, viewables);
            // But we need them in the message itself for handling during commit()
            mMessage.mHtml = tempBody.mHtmlContent;
            mMessage.mText = tempBody.mTextContent;
        } catch (MessagingException e) {
            // This would most likely indicate a broken stream
            throw new IOException(e);
        }
    }

    public void parseProperties() throws IOException {
        while (nextTag(Tags.ITEMS_PROPERTIES) != END) {
            switch (tag) {
                case Tags.BASE_BODY:
                    parseBody();
                    break;
                case Tags.EMAIL_MIME_DATA:
                    parseMIMEBody(getValue());
                    break;
                case Tags.EMAIL_BODY:
                    String text = getValue();
                    mMessage.mText = text;
                    break;
                default:
                    skipTag();
            }
        }
    }

    public void parseFetch() throws IOException {
        while (nextTag(Tags.ITEMS_FETCH) != END) {
            if (tag == Tags.ITEMS_PROPERTIES) {
                parseProperties();
            } else {
                skipTag();
            }
        }
    }

    public void parseResponse() throws IOException {
        while (nextTag(Tags.ITEMS_RESPONSE) != END) {
            if (tag == Tags.ITEMS_FETCH) {
                parseFetch();
            } else {
                skipTag();
            }
        }
    }

    @Override
    public boolean parse() throws IOException {
        boolean res = false;
        if (nextTag(START_DOCUMENT) != Tags.ITEMS_ITEMS) {
            throw new IOException();
        }
        while (nextTag(START_DOCUMENT) != END_DOCUMENT) {
            if (tag == Tags.ITEMS_STATUS) {
                // save the status code.
                mStatusCode = getValueInt();
            } else if (tag == Tags.ITEMS_RESPONSE) {
                parseResponse();
            } else {
                skipTag();
            }
        }
        return res;
    }

}
