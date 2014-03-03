package com.MeadowEast.audiotest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

public class Decompress {

	private ZipFile zipFile;
	private String _zipFilename;
	private Context _context;

	public Decompress(String zipFile, Context ctx) {
		_zipFilename = zipFile;
		_context = ctx;
	}

	public void unzip() {
		try {
			// Using assets
			// InputStream in = _context.getAssets().open(_zipFile);

			// Using external file system
			File mainDir = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath()
					+ "/Android/data/com.MeadowEast.audiotest/files");
			File zf = new File(mainDir, _zipFilename);
			FileInputStream in = new FileInputStream(zf);

			ZipInputStream zin = new ZipInputStream(in);
			ZipEntry ze = null;
			while ((ze = zin.getNextEntry()) != null) {
				Log.v("Decompress", "Unzipping " + ze.getName());

				if (ze.isDirectory()) {
					// Sub-directories do not work if we're using private file
					// storage
					continue;
				} else {
					FileOutputStream fout = _context.openFileOutput(
							ze.getName(), Context.MODE_PRIVATE);
					byte[] buffer = new byte[4096];
					for (int c = zin.read(buffer); c != -1; c = zin
							.read(buffer)) {
						fout.write(buffer, 0, c);
					}

					zin.closeEntry();
					fout.close();
				}

			}
			zin.close();
		} catch (Exception e) {
			Log.e("Decompress", "unzip", e);
		}

	}

	public InputStream getSample(String filename) {
		InputStream sample = null;
		
		try {
			sample = zipFile.getInputStream(zipFile
					.getEntry(filename));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		return sample;
	}

	public String[] getList() {
		// Open the zip file if it didn't happen yet (probably didn't)
		if (zipFile == null) {
			try {
				// Using external file system
				File mainDir = new File(Environment.getExternalStorageDirectory()
						.getAbsolutePath()
						+ "/Android/data/com.MeadowEast.audiotest/files");
				zipFile = new ZipFile(new File(mainDir, _zipFilename));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// Go through each entry and add it to the array list
		// Can't use an array because we don't know how many entries are in the enumeration.
		Enumeration e = zipFile.entries();
		ArrayList<String> list = new ArrayList<String>();
		while (e.hasMoreElements()) {
			ZipEntry entry = (ZipEntry) e.nextElement();
			list.add(entry.getName());			
		}
		
		// Convert to an array
		return list.toArray(new String[0]);		
	}
}