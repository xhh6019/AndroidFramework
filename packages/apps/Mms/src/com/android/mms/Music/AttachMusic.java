package com.android.mms.music;


import com.android.mms.R;
import android.app.Activity;
import android.os.Bundle;
import com.android.mms.music.AttachMusicAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import java.io.File;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.util.Log;
import android.media.AudioManager;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import android.widget.Toast;



public class AttachMusic extends Activity {
	/** Called when the activity is first created. */
	public final static String ACTION_ATTACHMENT_MUSIC = "android.intent.action.ATTACHMUSIC";
	private Cursor c ;
	private ListView lv  ;
	private Button addBtn,backBtn;
       public MediaPlayer mMediaPlayer;
       private AttachMusicAdapter mAttachMusicAdapter ;
	public int PlayerStatus = 0; 
	public int PlayerInt = -1; 
	public String musicPath,URI_ID;
	public static final int STATE_STOP = 0;
	public static final int STATE_PREPARE  = 1;	
	public static final int STATE_PLAY = 2;			
	public static final int STATE_PAUSE = 3;		
	public Uri ATTACHMENT_MUSIC = null ;
	

    String[] mCursorCols = new String[] {   
    		MediaStore.Video.Media.TITLE,                       
    		MediaStore.Audio.Media.DURATION,                        
    		MediaStore.Audio.Media.ARTIST,                       
    		MediaStore.Audio.Media._ID,                               
    		MediaStore.Audio.Media.DISPLAY_NAME,           
    		MediaStore.Audio.Media.DATA
    };  
	

    @Override
	public void onCreate(Bundle savedInstanceState) {
   	     super.onCreate(savedInstanceState);
    	     setContentView(R.layout.attachment_music);
		mMediaPlayer = new MediaPlayer();       
		RefreshCor()  ;
		musicList()  ;
		addButton()  ;
		}
       
	public void musicList(){
		lv = (ListView)findViewById(R.id.listView_attaMusic);
		mAttachMusicAdapter = new AttachMusicAdapter(this, c);
		lv.setAdapter(mAttachMusicAdapter);
		lv.setOnItemClickListener(new OnItemClickListener(){
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,long arg3) {
			// TODO Auto-generated method stub
			musicPath = getDataByPos(c,arg2)  ;
			if(PlayerInt != arg2){				
			setDataSource(musicPath)  ;
			mMediaPlayer.start()  ;
			PlayerStatus = STATE_PLAY  ;
			PlayerInt = arg2;
			}else{
				if(mMediaPlayer.isPlaying()){
				mMediaPlayer.pause();
				PlayerStatus = STATE_PAUSE;
				}else{
				mMediaPlayer.start();
				PlayerStatus = STATE_PLAY  ;
				}
			}
			mAttachMusicAdapter.setPlayState(PlayerInt,PlayerStatus);
			complete(arg2,PlayerStatus) ;
		}})  ;
	}

	public void addButton(){
		addBtn = (Button)findViewById(R.id.buttonAdd);
		backBtn = (Button)findViewById(R.id.buttonBack);
		addBtn.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				// TODO Auto-generated method stub
				if(PlayerInt != -1){
					String action = getIntent().getAction();
					if (ACTION_ATTACHMENT_MUSIC.equals(action)) {
					setResult(RESULT_OK, new Intent().setData(ATTACHMENT_MUSIC));
					finish();
					return; 
					}
				}else{
					Toast.makeText(AttachMusic.this, getString(R.string.add_choose_music),Toast.LENGTH_SHORT).show()   ;
				}
			}
		})  ;
		backBtn.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				// TODO Auto-generated method stub
				finish();
				return;
		  		}
			
		})  ;
	}
      

	public void setDataSource(String path) {
		try {
			mMediaPlayer.reset();
			mMediaPlayer.setOnPreparedListener(null);
			mMediaPlayer.setDataSource(path);
			mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mMediaPlayer.prepare();
		} catch (IOException ex) {
			// TODO: notify the user why the file couldn't be opened
			return;
		} catch (IllegalArgumentException ex) {
			// TODO: notify the user why the file couldn't be opened
			return;
		}
	}
	
	public void RefreshCor(){
 	Uri MUSIC_URL = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;  
	c = getContentResolver().query(MUSIC_URL, mCursorCols, null, null, null);
	}
	
	public String getDataByPos(Cursor c,int position)
	  {   
		 c.moveToPosition(position);   
		 int dataColumn = c.getColumnIndex(MediaStore.Audio.Media.DATA);	
		 int idColumn = c.getColumnIndex(MediaStore.Audio.Media._ID);	
		 String data = c.getString(dataColumn);   
		 URI_ID = c.getString(idColumn);   
		 ATTACHMENT_MUSIC =Uri.parse("content://media/external/audio/media/"+URI_ID);
		 return data;	
	  }

	@Override
	public void onDestroy() {
		mMediaPlayer.stop()  ;
		c.close()  ;
		super.onDestroy();
	}

	private void complete(int i,int PS){
		final int CoArg2 = i	;
		mMediaPlayer.setOnCompletionListener(new OnCompletionListener(){
		 public void onCompletion(MediaPlayer mp) {
			 // TODO Auto-generated method stub
			 PlayerStatus = STATE_STOP  ;
			 mAttachMusicAdapter.setPlayState(CoArg2,PlayerStatus);
			 }
		});
	}	

}



