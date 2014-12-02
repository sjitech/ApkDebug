package jp.sji.sumatium.tool;

import com.android.sdklib.build.ApkBuilder;

import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlVisitor;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;
import pxb.android.axml.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ApkDebug {

	byte[] buf = new byte[64 * 1024];
	int buf_readLen;

	String libDir = new File(AxmlReader.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile().getParent();

	public static void main(String[] args) {
		new ApkDebug(args);
	}

	File inputApkFile;
	File outputApkFile;
	File fDebugKeyStoreFile;
	boolean changed;

	public ApkDebug(String[] args) {
		if (args.length < 2) {
			LOG("Need arguments: <inputApkFile> <outputApkFile> [debugKeyStoreFile]");
			System.exit(1);
		}

		try {
			inputApkFile = new File(args[0]);
			if (!inputApkFile.exists()) {
				LOG("file " + inputApkFile + " does not exist");
				System.exit(1);
			}
			LOG("use inputApkFile: " + inputApkFile);

			outputApkFile = new File(args[1]);
			if (!outputApkFile.exists()) {
				outputApkFile.createNewFile();
			}
			LOG("use outputApkFile: " + outputApkFile);

			String debugKeyStoreFile = args.length > 2 ? args[2] : "";
			if (debugKeyStoreFile.length() > 0) {
				fDebugKeyStoreFile = new File(debugKeyStoreFile);
				if (!fDebugKeyStoreFile.exists()) {
					LOG("file " + fDebugKeyStoreFile + " does not exist");
					System.exit(1);
				}
				LOG("use debugKeyStoreFile: " + fDebugKeyStoreFile);
			} else {
				LOG("debugKeyStoreFile is not specified. The result APK file will not be signed (so can not be installed/published directly).");
			}

			File fResZip = File.createTempFile("res", ".zip");
			try {
				ZipOutputStream zosResZip = new ZipOutputStream(new FileOutputStream(fResZip));
				try {
					modifyManifest(/*save result to:*/zosResZip);
					File fClassesDex = File.createTempFile("classes", ".dex");
					try {
						LOG("extract other files");
						extractFilesTo(fClassesDex, zosResZip);
						zosResZip.close();
						LOG("package all to APK");
						ApkBuilder apkBuilder = new ApkBuilder(outputApkFile, fResZip, fClassesDex, debugKeyStoreFile != null ? fDebugKeyStoreFile.getPath() : null, null);
						apkBuilder.setDebugMode(true);
						apkBuilder.sealApk();
						LOG("OK");
						LOG("");
					} finally {
						fClassesDex.delete();
					}
				} finally {
					zosResZip.close();
				}
			} finally {
				fResZip.delete();
			}

			System.exit(changed ? 0 : 2);

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void modifyManifest(ZipOutputStream zos) throws Exception {
		final boolean[] found = new boolean[1];
		final String NS = "http://schemas.android.com/apk/res/android";

		class MyNodeVisitor extends NodeVisitor {
			String nodeName = "";

			MyNodeVisitor(NodeVisitor nv, String nodeName) {
				super(nv);
				this.nodeName = nodeName;
			}

			@Override
			public NodeVisitor child(String ns, String name) {
				if (ns == null && ("manifest".equals(nodeName) || "application".equals(name))) {
					return new MyNodeVisitor(super.child(ns, name), name);
				}
				return super.child(ns, name);
			}

			@Override
			public void attr(String ns, String name, int resourceId, int type, Object val) {
				if ("application".equals(nodeName) && "debuggable".equals(name) && NS.equals(ns) && val != null && val instanceof Boolean && type == NodeVisitor.TYPE_INT_BOOLEAN) {
					found[0] = true;
					if (val.equals(true)) {
						LOG("have found " + val + " -------------");
					} else {
						LOG("have found " + val + " but val is not true, so change it -------------");
						changed = true;
						val = true;
					}
				}
				super.attr(ns, name, resourceId, type, val);
			}

			@Override
			public void end() {
				if ("application".equals(nodeName)) {
					if (!found[0]) {
						LOG("add android:debuggable=\"true\" ++++++++++++++++");
						super.attr(NS, "debuggable", R.attr.debuggable, TYPE_INT_BOOLEAN, true);
						changed = true;
					}
				}
				super.end();
			}
		}

		ZipInputStream in = new ZipInputStream(new FileInputStream(inputApkFile));
		try {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				if (!entry.isDirectory() && entry.getName().equals("AndroidManifest.xml")) {
					break;
				}
			}

			if (entry != null) {
				zos.putNextEntry(new ZipEntry(entry.getName()));

				byte[] oldData;
				{
					ByteArrayOutputStream bos = new ByteArrayOutputStream(buf.length);
					while ((buf_readLen = in.read(buf)) > 0) {
						bos.write(buf, 0, buf_readLen);
					}
					oldData = bos.toByteArray();
				}
				in.read(oldData);
				AxmlReader ar = new AxmlReader(oldData);
				AxmlWriter aw = new AxmlWriter();
				ar.accept(new AxmlVisitor(aw) {

					@Override
					public NodeVisitor child(String ns, String name) {
						return new MyNodeVisitor(super.child(ns, name), name);
					}
				});

				zos.write(changed ? aw.toByteArray() : oldData);
			}
		} finally {
			in.close();
		}

	}

	void extractFilesTo(File fClassesDex, ZipOutputStream others) throws Exception {
		ZipInputStream in = new ZipInputStream(new FileInputStream(inputApkFile));
		try {
			FileOutputStream outClassesdex = new FileOutputStream(fClassesDex);
			try {
				ZipEntry entry;
				while ((entry = in.getNextEntry()) != null) {
					if (entry.isDirectory() || entry.getName().equals("AndroidManifest.xml")) {
						continue;
					}
					boolean matched = entry.getName().equals("classes.dex");
					if (!matched) {
						ZipEntry newEntry = entry.getMethod() == ZipEntry.DEFLATED ? new ZipEntry(entry.getName()) : new ZipEntry(entry);
						others.putNextEntry(newEntry);
					}
					while ((buf_readLen = in.read(buf)) > 0) {
						(matched ? outClassesdex : others).write(buf, 0, buf_readLen);
					}
				}
			} finally {
				outClassesdex.close();
			}
		} finally {
			in.close();
		}
	}

	interface ZipEntryFilter {
		boolean accept(ZipEntry entry);
	}

	void LOG(String s) {
		System.err.println(s);
	}
}