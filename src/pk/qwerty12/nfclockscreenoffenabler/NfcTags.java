package pk.qwerty12.nfclockscreenoffenabler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class NfcTags extends Activity {

	private SharedPreferences mPrefs = null;
	private NfcAdapter mNfcAdapter = null;
	private PendingIntent mPendingIntent;
	private IntentFilter[] mIntentFiltersArray;
	private String[][] mTechListsArray;
	private boolean mAlreadySetup;
	private ListView mListView;

	@SuppressLint("WorldReadableFiles")
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nfc_tags);

		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		getActionBar().setDisplayHomeAsUpEnabled(true);

		mPrefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);

		Set<String> nfcTags = mPrefs.getStringSet(Common.PREF_NFC_KEYS, null);

		mListView = (ListView) findViewById(R.id.listView);
		mListView.setOnItemLongClickListener(new ListView.OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View view, int position, long id) {
				@SuppressWarnings("unchecked")
				ArrayAdapter<String> adapter = (ArrayAdapter<String>) mListView.getAdapter();
				adapter.remove(adapter.getItem(position));
				mListView.setAdapter(adapter);

				return false;
			}

		});
		setupListViewFromSet(nfcTags);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		Editor editor = mPrefs.edit();
		Set<String> set = new HashSet<String>();
		@SuppressWarnings("unchecked")
		ArrayAdapter<String> adapter = (ArrayAdapter<String>) mListView.getAdapter();
		for (int i = 0; i < adapter.getCount(); i++) {
			String current = adapter.getItem(i);
			set.add(current);
		}

		editor.putStringSet(Common.PREF_NFC_KEYS, set);
		editor.commit();

		Intent i = new Intent(Common.SETTINGS_UPDATED_INTENT);
		sendBroadcast(i);
		super.onBackPressed();
	}

	private void setupListViewFromSet(Set<String> nfcTags) {
		ArrayList<String> tagIDs = new ArrayList<String>();
		if (nfcTags != null) {
			for (String tagID : nfcTags)
				tagIDs.add(tagID);
		}
		ArrayAdapter<String> arrayAdapter =      
				new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, tagIDs);
		mListView.setAdapter(arrayAdapter); 
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.nfc_tags, menu);
		return true;
	}

	@Override
	protected void onPause() {
		if (mNfcAdapter == null)
			mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

		if (mNfcAdapter != null) {
			mNfcAdapter.disableForegroundDispatch(this);
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mNfcAdapter == null)
			mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

		if (mNfcAdapter != null) {
			setUpForegroundDispatchSystem();
			mNfcAdapter.enableForegroundDispatch(this, mPendingIntent,
					mIntentFiltersArray, mTechListsArray);
		}
	}

	private void setUpForegroundDispatchSystem() {
		if (mAlreadySetup)
			return;
		mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		try {
			ndef.addDataType("*/*");    /* Handles all MIME based dispatches. 
                                           You should specify only the ones that you need. */
			ndef.addDataScheme("http");
		}
		catch (MalformedMimeTypeException e) {
			throw new RuntimeException("fail", e);
		}
		mIntentFiltersArray = new IntentFilter[] {ndef};
		mTechListsArray = new String[][] { new String[] { MifareUltralight.class.getName(), Ndef.class.getName(), NfcA.class.getName()},
				new String[] { MifareClassic.class.getName(), Ndef.class.getName(), NfcA.class.getName()}};

		mAlreadySetup = true;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// fetch the tag from the intent
		Tag t = (Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		Log.v("NFC", "{"+t+"}");

		String uuid = Common.byteArrayToHexString(t.getId());

		@SuppressWarnings("unchecked")
		ArrayAdapter<String> adapter = (ArrayAdapter<String>) mListView.getAdapter();
		for (int i = 0; i < adapter.getCount(); i++) {
			String current = adapter.getItem(i);
			if (current.equals(uuid)) {
				Toast.makeText(this, R.string.tag_already_added, Toast.LENGTH_SHORT).show();
				return;
			}
		}

		adapter.add(uuid);
		mListView.setAdapter(adapter);
	}
}
