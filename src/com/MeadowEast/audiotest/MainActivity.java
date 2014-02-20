package com.MeadowEast.audiotest;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
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
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.doomonafireball.betterpickers.hmspicker.HmsPickerBuilder;
import com.doomonafireball.betterpickers.hmspicker.HmsPickerDialogFragment;

public class MainActivity extends FragmentActivity implements
		HmsPickerDialogFragment.HmsPickerDialogHandler, OnClickListener,
		OnLongClickListener, OnTouchListener {

	public enum PracticeMode {
		LISTENING, READING
	};

	private PracticeMode practiceMode;
	private MediaPlayer mp;
	private String[] cliplist;
	private String[] enabledClipList;
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
	private Toast thumbsToast;
	private boolean goalCompletionNotified;
	private SharedPreferences sharedPref;
	private boolean[] activeClips;
	static final String TAG = "CAT";
	private float[] clipPreference;
	private boolean errorShowing;

	private void readClipInfo() throws TingShuoException {
		hanzi = new HashMap<String, String>();
		instructions = new HashMap<String, String>();
		clipHistory = new ArrayList<String>();

		// Open the clipinfo.txt asset
		InputStream clipInfo = null;
		try {
			clipInfo = getAssets().open("clipinfo.txt");
		} catch (IOException e1) {
			throw new TingShuoException("There was a problem opening the clip information file.");
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
			throw new TingShuoException("There was a problem reading the clip information file.");
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
		if (clockRunning)
			elapsedMillis += System.currentTimeMillis() - start;
		else
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

	private void displayHanzi(String s) {
		TextView t = (TextView) findViewById(R.id.hanziTextView);
		t.setText(s);
	}

	private void setSample(String filename) throws TingShuoException {
		// Show the thumb buttons
		findViewById(R.id.thumbWrapper).setVisibility(View.VISIBLE);

		// Open the sample
		sampleFilename = filename;
		try {
			sample = openFileInput(sampleFilename);
			// sample = getAssets().open(clipDir + "/" + sampleFilename);
		} catch (IOException e1) {
			throw new TingShuoException("There was an error opening a clip.");
		}
		key = sampleFilename;

		// Remove ".mp3" from the end of the filename
		key = key.substring(0, key.length() - 4);

		// Display the instruction
		if (practiceMode == PracticeMode.LISTENING) {
			// Display the instruction
			displayInstruction();
		} else {
			displayHanzi();
		}
	}

	private void playSample() throws TingShuoException {
		// Clear the hanzi if in listening mode
		if (practiceMode == PracticeMode.LISTENING)
			displayHanzi("");

		// Start the clock if off
		if (!clockRunning)
			toggleClock();

		if (sample != null) {
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
				throw new TingShuoException("There was an error playing the clip.");
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
		try {
			setSample(clipHistory.remove(index));
		} catch (TingShuoException e) {
			errorScreen(e.getMessage());
			return;
		}
		
		try {
			playSample();
		} catch (TingShuoException e) {
			errorScreen(e.getMessage());
			return;
		}
	}

	public void setPracticeMode(PracticeMode pm) {
		practiceMode = pm;

		// Save the practice mode
		SharedPreferences.Editor editor = sharedPref.edit();
		String mode;
		if (practiceMode == PracticeMode.LISTENING)
			mode = "listening";
		else
			mode = "reading";
		editor.putString("practiceMode", mode);
		editor.commit();

		// Render the layout
		if (practiceMode == PracticeMode.LISTENING)
			setContentView(R.layout.listening_mode);
		else if (practiceMode == PracticeMode.READING)
			setContentView(R.layout.reading_mode);

		// Hide the thumb buttons by default
		findViewById(R.id.thumbWrapper).setVisibility(View.INVISIBLE);

		// Set event handlers
		findViewById(R.id.playButton).setOnClickListener(this);
		findViewById(R.id.repeatButton).setOnClickListener(this);
		findViewById(R.id.timerTextView).setOnClickListener(this);
		findViewById(R.id.thumbsDown).setOnClickListener(this);
		findViewById(R.id.thumbsUp).setOnClickListener(this);
		findViewById(R.id.hanziTextView).setOnLongClickListener(this);
		if (practiceMode == PracticeMode.LISTENING)
			findViewById(R.id.hanziButton).setOnClickListener(this);

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

		// Show the thumb buttons if there is a current sample
		if (key != null)
			findViewById(R.id.thumbWrapper).setVisibility(View.VISIBLE);

		// Display the appropriate info
		if (practiceMode == PracticeMode.LISTENING) {
			if (key != null)
				displayInstruction();
		} else {
			displayInstruction("reading mode");
			if (key != null)
				displayHanzi();
		}
	}

	public void displayInstruction() {
		displayInstruction(getInstruction(key));
	}

	public void displayInstruction(String instruction) {
		TextView t = (TextView) findViewById(R.id.instructionTextView);
		t.setText(instruction);
	}

	public void displayHanzi() {
		displayHanzi(hanzi.get(key));
	}

	public void resetClipPreference() {
		for (int i = 0; i < clipPreference.length; i++)
			clipPreference[i] = (float) 1.0;
	}

	public void errorScreen(String error) {
		errorShowing = true;
		
		// Stop the clock updating
		if (clockHandler != null)
			clockHandler.removeCallbacks(updateTimeTask);
		
		// Change the layout and set the error text
		setContentView(R.layout.error);
		TextView v = (TextView) findViewById(R.id.errorText);
		v.setText(error);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "testing only");
		sharedPref = getPreferences(Context.MODE_PRIVATE);

		// Decompress the zip only if files have changed
		int assetHash = getAssets().hashCode();
		if (assetHash != sharedPref.getInt("assetHash", -1)) {
			// Store the new hash
			SharedPreferences.Editor editor = sharedPref.edit();
			editor.putInt("assetHash", assetHash);
			editor.commit();

			// Decompress the zip file
			Decompress d = new Decompress(zipFile, this);
			d.unzip();
		}

		cliplist = getFilesDir().list();
		enabledClipList = cliplist.clone();
		clipPreference = new float[cliplist.length];
		resetClipPreference();
		activeClips = new boolean[cliplist.length];
		// Initialize activeClips to true
		for (int i = 0; i < activeClips.length; i++) {
			activeClips[i] = true;
		}
		try {
			readClipInfo();
		} catch (TingShuoException e1) {
			errorScreen(e1.getMessage());
			return;
		}

		// Set the practice mode
		// Note: this logic has listening mode as default
		String mode = sharedPref.getString("practiceMode", null);
		if (mode == "reading")
			setPracticeMode(PracticeMode.READING);
		else
			setPracticeMode(PracticeMode.LISTENING);

		rnd = new Random();
		clockHandler = new Handler();
		start = System.currentTimeMillis();
		elapsedMillis = 0L;
		goalSeconds = sharedPref.getInt("goalSeconds", 0);
		goalCompletionNotified = false;
		clockRunning = false;
		createUpdateTimeTask();

		if (savedInstanceState != null) {
			// Don't do anything if there was an error
			if (savedInstanceState.getBoolean("errorShowing"))
				return;
			
			// Restore the clip history
			clipHistory = savedInstanceState.getBundle("data")
					.getStringArrayList("clipHistory");

			elapsedMillis = savedInstanceState.getLong("elapsedMillis");
			Log.d(TAG, "elapsedMillis restored to" + elapsedMillis);
			key = savedInstanceState.getString("key");
			sampleFilename = savedInstanceState.getString("sample");
			goalCompletionNotified = savedInstanceState
					.getBoolean("goalCompletionNotified");

			enabledClipList = savedInstanceState
					.getStringArray("enabledClipList");

			// Show the thumb buttons
			findViewById(R.id.thumbWrapper).setVisibility(View.VISIBLE);

			// Retrieve the practice mode (0 -> not stored, 1 -> listening, 2 ->
			// reading)
			int practiceModeFlag = savedInstanceState
					.getInt("practiceModeFlag");
			if (practiceModeFlag == 1)
				practiceMode = PracticeMode.LISTENING;
			else if (practiceModeFlag == 2)
				practiceMode = PracticeMode.READING;

			// Set the practice mode
			setPracticeMode(practiceMode);

			if (sampleFilename.length() > 0) {
				try {
					sample = openFileInput(sampleFilename);
				} catch (IOException e) {
					errorScreen("The filename from your saved session cannot be found.");
					return;
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
				displayInstruction(instruction);
			}
		}
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options, menu);
		return true;
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		// Only show "clear time goal" if there's a time goal
		menu.findItem(R.id.clearTimeGoal).setVisible(goalSeconds != 0);

		// Only show clear preferences if they're not all one
		menu.findItem(R.id.clearPreferences).setVisible(false);
		for (int i = 0; i < clipPreference.length; i++) {
			if (clipPreference[i] != 1.0) {
				menu.findItem(R.id.clearPreferences).setVisible(true);
				break;
			}
		}

		// Set the string for the button that toggles practice mode
		String modeSwitchString;
		if (practiceMode == PracticeMode.LISTENING)
			modeSwitchString = getString(R.string.setReadingMode);
		else
			modeSwitchString = getString(R.string.setListeningMode);
		menu.findItem(R.id.toggleMode).setTitle(modeSwitchString);

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
			editor.putInt("goalSeconds", 0);
			editor.commit();
			return true;
		case R.id.toggleMode:
			if (practiceMode == PracticeMode.LISTENING)
				setPracticeMode(PracticeMode.READING);
			else
				setPracticeMode(PracticeMode.LISTENING);
			return true;
		case R.id.clearPreferences:
			resetClipPreference();
			return true;
		case R.id.selectClips:
			// Create a dialog (Android's pop-up)
			// Note: Setup correctly titled buttons with placeholder onClicks
			// (they will be overwritten further down)
			View dialogLayout = getLayoutInflater().inflate(
					R.layout.select_clips, null);
			final AlertDialog dialog = new AlertDialog.Builder(this)
					.setView(dialogLayout)
					.setTitle(R.string.selectClips)
					.setPositiveButton(R.string.selectionComplete,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
								}
							})
					.setNegativeButton(R.string.selectAll,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
								}
							})
					.setNeutralButton(R.string.deselectAll,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
								}
							}).create();

			// Populate the list view
			ListView listView = (ListView) dialogLayout
					.findViewById(R.id.clipsListView);
			listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
			ArrayAdapter<String> adp = new ArrayAdapter<String>(this,
					android.R.layout.simple_list_item_multiple_choice, cliplist);
			listView.setAdapter(adp);

			// Select the enabled clips in the list view
			for (int i = 0; i < adp.getCount(); i++)
				listView.setItemChecked(i, activeClips[i]);

			// Display the dialog
			dialog.show();

			// Override the buttons to stop the automatic dialog dismissal
			dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
					new View.OnClickListener() {
						public void onClick(View v) {
							ListView lv = (ListView) dialog
									.findViewById(R.id.clipsListView);
							SparseBooleanArray checked = lv
									.getCheckedItemPositions();

							// Only dismiss if something was picked
							if (checked.size() == 0) {
								Toast.makeText(MainActivity.this,
										"You have to pick at least one clip.",
										Toast.LENGTH_SHORT).show();
							} else {
								// Copy values into the boolean array
								int totalActive = 0;
								for (int i = 0; i < checked.size(); i++) {
									activeClips[i] = checked.get(i);
									if (activeClips[i])
										totalActive++;
								}

								// Create the new list of available filenames
								int current = 0;
								enabledClipList = new String[totalActive];
								for (int i = 0; i < activeClips.length; i++) {
									if (activeClips[i]) {
										enabledClipList[current] = cliplist[i];
										current++;
									}
								}

								// Close the dialog
								dialog.dismiss();
							}
						}
					});
			dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(
					new View.OnClickListener() {
						public void onClick(View v) {
							ListView lv = (ListView) dialog
									.findViewById(R.id.clipsListView);
							setAllListViewItemsTo(lv, true);
						}
					});
			dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(
					new OnClickListener() {
						public void onClick(View v) {
							ListView lv = (ListView) dialog
									.findViewById(R.id.clipsListView);
							setAllListViewItemsTo(lv, false);
						}
					});
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public void setAllListViewItemsTo(ListView lv, boolean value) {
		for (int i = 0; i < lv.getAdapter().getCount(); i++) {
			lv.setItemChecked(i, value);
		}
	}

	public void onPause() {
		super.onPause();
		Log.d(TAG, "!!!! onPause is being run");
		clockWasRunning = clockRunning;
		if (clockRunning)
			toggleClock();
	}

	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putBoolean("errorShowing", errorShowing);
		// Don't do anything if an error is showing
		if (errorShowing) {
			return;
		}
			
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
		outState.putBoolean("goalCompletionNotified", goalCompletionNotified);
		outState.putInt("goalSeconds", goalSeconds);
		outState.putStringArray("enabledClipList", enabledClipList);

		// Store the practice mode using an integer flag
		int practiceModeFlag = 0;
		if (practiceMode == PracticeMode.LISTENING)
			practiceModeFlag = 1;
		else if (practiceMode == PracticeMode.READING)
			practiceModeFlag = 2;
		outState.putInt("practiceModeFlag", practiceModeFlag);

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
		displayHanzi("");
		displayInstruction("");
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

	public String nextRandomClip() {
		int index;
		float sum = 0;

		// Add up all the probabilities
		for (int i = 0; i < enabledClipList.length; i++) {
			index = Arrays.asList(cliplist).indexOf(enabledClipList[i]);
			sum += clipPreference[index];
		}

		// Find a value from 0 to the sum of the probabilities
		double threshold = sum * rnd.nextDouble();

		// Find the string who's probability pushes the sum above the threshold
		sum = 0;
		for (int i = 0; i < enabledClipList.length; i++) {
			index = Arrays.asList(cliplist).indexOf(enabledClipList[i]);
			sum += clipPreference[index];

			if (sum > threshold)
				return enabledClipList[i];
		}

		// There is no next clip. This should not happen.
		return null;
	}

	public void raiseClipPreference() {
		int index = Arrays.asList(cliplist).indexOf(sampleFilename);
		float preference = clipPreference[index];

		// Cancel any existing toast
		if (thumbsToast != null)
			thumbsToast.cancel();

		// Don't raise the clip preference higher than 8
		if (preference == 8) {
			thumbsToast = Toast.makeText(this,
					"This clip's preference cannot be set higher.",
					Toast.LENGTH_SHORT);
			thumbsToast.show();
			return;
		}

		// Double the preference
		clipPreference[index] = preference * 2;
		thumbsToast = Toast.makeText(this,
				"This clip will be selected more often.", Toast.LENGTH_SHORT);
		thumbsToast.show();
	}

	public void lowerClipPreference() {
		int index = Arrays.asList(cliplist).indexOf(sampleFilename);

		// Halve the preference
		clipPreference[index] /= 2;

		// Cancel any existing toast
		if (thumbsToast != null)
			thumbsToast.cancel();

		thumbsToast = Toast
				.makeText(this, "This clip will be selected half as often.",
						Toast.LENGTH_SHORT);
		thumbsToast.show();
	}

	public void onClick(View v) {
		switch (v.getId()) {

		case R.id.playButton:
			// Save the current sample
			if (sampleFilename != null) {
				clipHistory.add(sampleFilename);
			}
			try {
				setSample(nextRandomClip());
			} catch (TingShuoException e) {
				errorScreen(e.getMessage());
				return;
			}

		case R.id.repeatButton:
			try {
				playSample();
			} catch (TingShuoException e) {
				errorScreen(e.getMessage());
				return;
			}
			break;

		case R.id.hanziButton:
			if (!clockRunning)
				toggleClock();
			if (sample != null)
				displayHanzi(); // Should add default value: error
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
		case R.id.thumbsDown:
			lowerClipPreference();
			break;
		case R.id.thumbsUp:
			raiseClipPreference();
			break;
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			Log.d(TAG, "llkj");
			if (errorShowing) {
				finish();
			} else {
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
			}
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