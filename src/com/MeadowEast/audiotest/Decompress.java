package com.MeadowEast.audiotest;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.content.Context;
import android.util.Log;

public class Decompress {

	private String _zipFile;
	private Context _context;

	public Decompress(String zipFile, Context ctx) {
		_zipFile = zipFile;
		_context = ctx;
	}

	public void unzip() {
		try {
			InputStream in = _context.getAssets().open(_zipFile);
			ZipInputStream zin = new ZipInputStream(in);
			ZipEntry ze = null;
			while ((ze = zin.getNextEntry()) != null) {
				Log.v("Decompress", "Unzipping " + ze.getName());

				if (ze.isDirectory()) {
					// Sub-directories do not work if we're using private file storage
					continue;
				} else {
					FileOutputStream fout = _context.openFileOutput(ze.getName(), Context.MODE_PRIVATE);
					byte[] buffer = new byte[4096];
					for (int c = zin.read(buffer); c != -1; c = zin.read(buffer))
					{
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
}