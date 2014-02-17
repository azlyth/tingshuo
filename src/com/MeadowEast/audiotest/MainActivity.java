package com.MeadowEast.audiotest;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.widget.TextView;
import android.widget.Toast;

import com.doomonafireball.betterpickers.hmspicker.HmsPickerBuilder;
import com.doomonafireball.betterpickers.hmspicker.HmsPickerDialogFragment;

public class MainActivity extends FragmentActivity implements
		HmsPickerDialogFragment.HmsPickerDialogHandler, OnClickListener,
		OnLongClickListener, OnTouchListener {

	private MediaPlayer mp;
	private String[] cliplist;
	private ArrayList<String> clipHistory;
	private FileInputStream sample;
	private String sampleFilename;
	private String zipFile = "tingshuo-clips.zip";
	private Random rnd;
	private Handler clockHandler;
	private Runnable updateTimeTask;
	private boolean clockRunning;
	private boolean clockWasRunning;
	private Long elapsedMillis;
	private Long start;
	private int goalSeconds;
	private Map<String, String> hanzi;
	private Map<String, String> instructions;
	private String key;
	private Toast no_previous_toast;
	private boolean goalCompletionNotified;
	private SharedPreferences sharedPref;
	static final String TAG = "CAT";

	private void readClipInfo() {
		hanzi = new HashMap<String, String>();
		instructions = new HashMap<String, String>();
		clipHistory = new ArrayList<String>();

		// Open the clipinfo.txt asset
		InputStream clipInfo = null;
		try {
			clipInfo = getAssets().open("clipinfo.txt");
		} catch (IOException e1) {
			// TODO: handle nicely (and exit probably)
			e1.printStackTrace();
		}

		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(
					clipInfo));
			String line;
			while ((line = in.readLine()) != null) {
				String fixedline = new String(line.getBytes(), "utf-8");
				String[] fields = fixedline.split("\\t");
				if (fields.length == 3) {
					hanzi.put(fields[0], fields[1]);
					instructions.put(fields[0], fields[2]);
				} else {
					Log.d(TAG, "Bad line: " + fields.length + " elements");
					Log.d(TAG, fixedline);
				}
			}
			in.close();
		} catch (Exception e) {
			Log.d(TAG, "Problem reading clipinfo");
		}
	}

	private String getInstruction(String key) {
		String instructionCodes = instructions.get(key);
		int n = instructionCodes.length();
		if (n == 0) {
			return "No instruction codes for " + key;
		}
		int index = rnd.nextInt(n);
		switch (instructionCodes.charAt(index)) {
		case 'C':
			return "continue the conversation";
		case 'A':
			return "answer the question";
		case 'R':
			return "repeat";
		case 'P':
			return "paraphrase";
		case 'Q':
			return "ask questions";
		case 'V':
			return "create variations";
		default:
			return "Bad instruction code " + instructionCodes.charAt(index)
					+ " for " + key;
		}
	}

	private void toggleClock() {
		if (clockRunning) {
			elapsedMillis += System.currentTimeMillis() - start;
			setHanzi("");
		} else
			start = System.currentTimeMillis();
		clockRunning = !clockRunning;
		clockHandler.removeCallbacks(updateTimeTask);
		if (clockRunning)
			clockHandler.postDelayed(updateTimeTask, 200);
	}

	private void showTime(Long totalMillis) {
		int seconds = (int) (totalMillis / 1000);

		// Beep if we've the timer has gone above the goal time
		if (goalSeconds != 0 && seconds >= goalSeconds) {
			if (!goalCompletionNotified) {
				// Make a beep
				ToneGenerator toneG = new ToneGenerator(
						AudioManager.STREAM_ALARM, 90);
				toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250);
				goalCompletionNotified = true;

			}
		}

		int minutes = seconds / 60;
		seconds = seconds % 60;
		TextView t = (TextView) findViewById(R.id.timerTextView);
		if (seconds < 10)
			t.setText("" + minutes + ":0" + seconds);
		else
			t.setText("" + minutes + ":" + seconds);
	}

	private void createUpdateTimeTask() {
		updateTimeTask = new Runnable() {
			public void run() {
				Long totalMillis = elapsedMillis + System.currentTimeMillis()
						- start;
				showTime(totalMillis);
				clockHandler.postDelayed(this, 1000);
			}
		};
	}

	private void setHanzi(String s) {
		TextView t = (TextView) findViewById(R.id.hanziTextView);
		t.setText(s);
	}

	private void setSample(String filename) {
		sampleFilename = filename;
		try {
			sample = openFileInput(sampleFilename);
			// sample = getAssets().open(clipDir + "/" + sampleFilename);
		} catch (IOException e1) {
			// File with filename doesn't exist
			e1.printStackTrace();
		}
		key = sampleFilename;

		// Remove ".mp3" from the end of the filename
		key = key.substring(0, key.length() - 4);

		TextView t = (TextView) findViewById(R.id.instructionTextView);
		t.setText(getInstruction(key));
	}

	private void playSample() {
		if (!clockRunning)
			toggleClock();
		if (sample != null) {
			setHanzi("");
			if (mp != null) {
				mp.stop();
				mp.release();
			}
			mp = new MediaPlayer();
			mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
			try {
				mp.setDataSource(sample.getFD());
				mp.prepare();
				mp.start();
			} catch (Exception e) {
				Log.d(TAG, "Couldn't get mp3 file");
			}
		}
	}

	private void playLastSample() {
		if (clipHistory.size() == 0) {
			if (no_previous_toast != null) {
				no_previous_toast.cancel();
			}
			no_previous_toast = Toast.makeText(MainActivity.this,
					"You have no previous clips.", Toast.LENGTH_SHORT);
			no_previous_toast.show();
			return;
		}

		int index = clipHistory.size() - 1;
		setSample(clipHistory.remove(index));
		playSample();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "testing only");

		// Decompress the zip file
		Decompress d = new Decompress(zipFile, this);
		d.unzip();

		cliplist = getFilesDir().list();

		readClipInfo();
		setContentView(R.layout.activity_main);

		sharedPref = getPreferences(Context.MODE_PRIVATE);
		rnd = new Random();
		clockHandler = new Handler();
		start = System.currentTimeMillis();
		elapsedMillis = 0L;
		goalSeconds = sharedPref.getInt("goalSeconds", 0);
		goalCompletionNotified = false;
		clockRunning = false;
		createUpdateTimeTask();

		findViewById(R.id.playButton).setOnClickListener(this);
		findViewById(R.id.repeatButton).setOnClickListener(this);
		findViewById(R.id.hanziButton).setOnClickListener(this);
		findViewById(R.id.timerTextView).setOnClickListener(this);
		findViewById(R.id.hanziTextView).setOnLongClickListener(this);

		findViewById(R.id.pauseButton).setOnClickListener(
				new OnClickListener() {
					public void onClick(View v) {
						toggleClock();
					}
				});

		findViewById(R.id.hanziTextView).setOnTouchListener(
				new OnSwipeTouchListener(this) {
					public void onSwipeRight() {
						playLastSample();
					}
				});

		if (savedInstanceState != null) {
			// Restore the clip history
			clipHistory = savedInstanceState.getBundle("data")
					.getStringArrayList("clipHistory");

			elapsedMillis = savedInstanceState.getLong("elapsedMillis");
			Log.d(TAG, "elapsedMillis restored to" + elapsedMillis);
			key = savedInstanceState.getString("key");
			sampleFilename = savedInstanceState.getString("sample");
			if (sampleFilename.length() > 0) {
				try {
					sample = openFileInput(sampleFilename);
				} catch (IOException e) {
					// Filename doesn't match a file.
					e.printStackTrace();
				}
			}
			if (savedInstanceState.getBoolean("running"))
				toggleClock();
			else
				showTime(elapsedMillis);
			Log.d(TAG, "About to restore instruction");
			String instruction = savedInstanceState.getString("instruction");
			if (instruction.length() > 0) {
				Log.d(TAG, "Restoring instruction value of " + instruction);
				TextView t = (TextView) findViewById(R.id.instructionTextView);
				t.setText(instruction);
			}
		}
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options, menu);
		return true;
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.clearTimeGoal).setVisible(goalSeconds != 0);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.setTimeGoal:
			HmsPickerBuilder hpb = new HmsPickerBuilder().setFragmentManager(
					getSupportFragmentManager()).setStyleResId(
					R.style.BetterPickersDialogFragment_Light);
			hpb.show();
			return true;
		case R.id.clearTimeGoal:
			goalSeconds = 0;
			goalCompletionNotified = false;
			SharedPreferences.Editor editor = sharedPref.edit();
			// We can call clear because there are no other preferences stored
			// as of right now.
			editor.clear();
			editor.commit();
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	// @Override
	// public void onDialogHmsSet(int reference, int hours, int minutes,
	// int seconds) {
	// text.setText("" + hours + ":" + minutes + ":" + seconds);
	// }

	public void onPause() {
		super.onPause();
		Log.d(TAG, "!!!! onPause is being run");
		clockWasRunning = clockRunning;
		if (clockRunning)
			toggleClock();
	}

	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		String sampleName = "";
		if (sample != null)
			sampleName = sampleFilename;
		outState.putString("sample", sampleName);
		// onPause has stopped the clock if it was running, so we just save
		// elapsedMillis
		outState.putLong("elapsedMillis", elapsedMillis);
		TextView t = (TextView) findViewById(R.id.instructionTextView);
		outState.putString("instruction", t.getText().toString());
		outState.putString("key", key);
		outState.putBoolean("running", clockWasRunning);
		outState.putInt("goalSeconds", goalSeconds);

		// Save the clip history
		Bundle bundle = new Bundle();
		bundle.putStringArrayList("clipHistory", clipHistory);
		outState.putBundle("data", bundle);
	}

	public void reset() {
		TextView t;
		clipHistory.clear();
		if (clockRunning)
			toggleClock();

		// Reset time data
		start = 0L;
		elapsedMillis = 0L;
		goalCompletionNotified = false;

		sample = null;
		t = (TextView) findViewById(R.id.timerTextView);
		t.setText("0:00");
		setHanzi("");
		t = (TextView) findViewById(R.id.instructionTextView);
		t.setText("");
	}

	public boolean onLongClick(View v) {
		switch (v.getId()) {
		case R.id.hanziTextView:
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("message/rfc822");
			intent.putExtra(Intent.EXTRA_EMAIL,
					new String[] { getString(R.string.authorEmail) });
			intent.putExtra(Intent.EXTRA_SUBJECT,
					"[TingShuo] Message to the author");
			// Include file information if we have it
			if (sampleFilename != null) {
				intent.putExtra(Intent.EXTRA_TEXT, "Instruction:\n"
						+ getInstruction(key) + "\n\nHanzi:\n" + hanzi.get(key)
						+ "\n\nFile:\n " + sampleFilename.substring(1)
						+ "\n\nMessage:\n");
			}
			startActivity(Intent.createChooser(intent, "Send mail to author"));
			Log.d(TAG, "Long clicked");
			break;
		}
		return true;
	}

	public void onClick(View v) {
		switch (v.getId()) {

		case R.id.playButton:
			// Save the current sample
			if (sampleFilename != null) {
				clipHistory.add(sampleFilename);
			}
			Integer index = rnd.nextInt(cliplist.length);
			setSample(cliplist[index]);

		case R.id.repeatButton:
			playSample();
			break;

		case R.id.hanziButton:
			if (!clockRunning)
				toggleClock();
			if (sample != null)
				setHanzi(hanzi.get(key)); // Should add default value: error
											// message if no hanzi for key
			break;

		case R.id.timerTextView:
			new AlertDialog.Builder(this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.reset)
					.setMessage(R.string.reallyReset)
					.setPositiveButton(R.string.yes,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									MainActivity.this.reset();
								}
							}).setNegativeButton(R.string.no, null).show();
			break;
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			Log.d(TAG, "llkj");
			new AlertDialog.Builder(this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.quit)
					.setMessage(R.string.reallyQuit)
					.setPositiveButton(R.string.yes,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									MainActivity.this.finish();
								}
							}).setNegativeButton(R.string.no, null).show();
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	public boolean onTouch(View v, MotionEvent event) {
		return false;
	}

	public void onDialogHmsSet(int reference, int hours, int minutes,
			int seconds) {
		goalSeconds = seconds;
		goalSeconds += minutes * 60;
		goalSeconds += hours * 60 * 60;

		// Save this value to shared preferences so we can keep it saved
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putInt("goalSeconds", goalSeconds);
		editor.commit();
	}

}
