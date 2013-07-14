package com.android.mms.music;


import com.android.mms.R;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.ImageButton;
import com.android.mms.music.AttachMusic;
import android.util.Log;



public class AttachMusicAdapter extends BaseAdapter {
	private Context ada_context  ;
	private Cursor ada_cursor  ;
	private int mCurPlayMusicIndex = -1;
	private int  mPlayState ;

	

	public AttachMusicAdapter(Context con, Cursor cur){
		this.ada_context = con  ;
		this.ada_cursor = cur  ;
	}

	public void setPlayState(int playIndex, int playState)
	{
		mCurPlayMusicIndex = playIndex;
		mPlayState = playState;
		notifyDataSetChanged();
	}

	public int getCurPlayIndex()
	{
		return mCurPlayMusicIndex;
	}
	
	public int getCurPlayState()
	{
		return mPlayState;
	}
	
	public String toTime(int time) {

        time /= 1000;
        int minute = time / 60;
        int second = time % 60;
        minute %= 60;
        return String.format("%02d:%02d", minute, second);
	}
	
	 public int getCount() {
		// TODO Auto-generated method stub
		return this.ada_cursor.getCount()  ;
	}

	public Object getItem(int position) {
		// TODO Auto-generated method stub
		return position  ;
	}

	public long getItemId(int position) {
		// TODO Auto-generated method stub
		return position  ;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		// TODO Auto-generated method stub
	
		if(convertView == null){
		convertView = LayoutInflater.from(ada_context).inflate(R.layout.mus_list_item,null);
			}
		showPlayStateIcon(convertView, position);
		ada_cursor.moveToPosition(position)  ;	
		TextView musicName = (TextView)convertView.findViewById(R.id.musicName) ;
		musicName.setText(ada_cursor.getString(0))  ;
		TextView musicTime = (TextView)convertView.findViewById(R.id.musicTime) ;
		musicTime.setText(toTime(ada_cursor.getInt(1)))  ;
		TextView musicArtist = (TextView)convertView.findViewById(R.id.musicAritst) ;
		musicArtist.setText(ada_cursor.getString(2))  ;
		TextView musicPos = (TextView)convertView.findViewById(R.id.musiclistPos) ;
		String strPosString = String.valueOf(position + 1) + ".";
		musicPos.setText(strPosString);
		return convertView;
	} 
		
	private void showPlayStateIcon(View view, int position)
	{
		ImageButton imageView = (ImageButton) view.findViewById(R.id.musicplaystate);
		if (position != mCurPlayMusicIndex)
		{
			imageView.setVisibility(View.GONE);
			return ;
		}
		
		imageView.setVisibility(View.VISIBLE);
		if (mPlayState == AttachMusic.STATE_PLAY)
		{
			imageView.setBackgroundResource(R.drawable.listicon);
		}else{
			imageView.setBackgroundResource(R.drawable.listicon_stop);
		}
	}
	

	
}
