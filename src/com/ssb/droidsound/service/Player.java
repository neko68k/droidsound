package com.ssb.droidsound.service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ssb.droidsound.FileIdentifier;
import com.ssb.droidsound.SongFile;
import com.ssb.droidsound.plugins.DroidSoundPlugin;
import com.ssb.droidsound.utils.NativeZipFile;

/*
 * The Player thread 
 */
public class Player implements Runnable {
	private static final String TAG = "Player";

	public enum State {
		STOPPED, PAUSED, PLAYING, SWITCHING
	};

	public enum Command {
		NO_COMMAND,
		STOP, // Unload and stop if playing
		PLAY, // Play new file or last file
		PLAY_MULTIPLE,
		// Only when Playing:
		PAUSE, UNPAUSE, SET_POS, SET_TUNE,
	};

	public static final int MSG_NEWSONG = 0;
	public static final int MSG_DONE = 2;
	public static final int MSG_STATE = 1;
	public static final int MSG_PROGRESS = 3;
	protected static final int MSG_SILENT = 4;
	public static final int MSG_SUBTUNE = 5;
	public static final int MSG_DETAILS = 6;
	public static final int MSG_INFO = 7;

	// public static final int MSG_SUBTUNE = 5;

	public static class SongInfo {
		String title;
		String author;
		String copyright;
		String type;
		boolean canSeek;
		// String game;
		int length;
		int subTunes;
		int startTune;
		public String[] details;
		public String subtuneTitle;
		public String subtuneAuthor;
		public String fileName;
		public String source;
		public byte[] md5;
	};

	private Handler mHandler;

	// Audio data
	private short[] samples;
	private AudioTrack audioTrack;
	private int bufSize;

	// Plugins
	private DroidSoundPlugin currentPlugin;
	private List<DroidSoundPlugin> plugins;

	// Incoming state
	private Object cmdLock = new Object();
	private Command command = Command.NO_COMMAND;
	private Object argument;

	// Player state
	// private int currentPosition;
	private int noPlayWait;
	private int lastPos;
	private int playPosOffset;
	private SongInfo currentSong = new SongInfo();

	// private Object songRef;

	private int currentTune;

	private File currentZipFile;
	private NativeZipFile currentZip;

	private volatile State currentState = State.STOPPED;
	private int silentPosition;

	int FREQ = 44100;
	private long audioTime;
	private int aCount;
	private long lastTime = -1;
	private long frameTime;
	// private int switchPos = -1;
	private boolean songEnded = false;
	private boolean reinitAudio;

	private boolean startedFromSub;
	private int lastLatency;
	private boolean firstData;
	private SongFile nextSong;
	//private String lastSubtuneTitle;
	//private String lastTitle;

	public Player(AudioManager am, Handler handler, Context ctx) {
		mHandler = handler;

		plugins = DroidSoundPlugin.createPluginList();

		silentPosition = -1;
		// Enough for 3000ms
		bufSize = 0x40000;

		samples = new short[bufSize / 2];
	}

	private File writeFile(String name, InputStream fs, boolean temp) throws IOException {
		File file;
		if(temp) {

			name = name.substring(name.lastIndexOf('/') + 1);

			int dot = name.indexOf('.');
			int lastDot = name.lastIndexOf('.');

			if(dot == -1) {
				file = File.createTempFile(name, "");
			} else {
				file = File.createTempFile(name.substring(0, dot + 1), name.substring(lastDot));
			}
		} else {
			file = new File(name);
		}

		FileOutputStream fo = new FileOutputStream(file);
		byte[] buffer = new byte[16384];

		while(true) {
			int rc = fs.read(buffer);
			if(rc <= 0) {
				break;
			}
			fo.write(buffer, 0, rc);
		}
		fo.close();
		return file;
	}

	public static String makeSize(long fileSize) {
		String s;
		if(fileSize < 10 * 1024) {
			s = String.format("%1.1fKB", (float) fileSize / 1024F);
		} else if(fileSize < 1024 * 1024) {
			s = String.format("%dKB", fileSize / 1024);
		} else if(fileSize < 10 * 1024 * 1024) {
			s = String.format("%1.1fMB", (float) fileSize / (1024F * 1024F));
		} else {
			s = String.format("%dMB", fileSize / (1024 * 1024));
		}
		return s;
	}

	private void startSong(SongFile song, SongFile song2) {
		nextSong = song2;
		startSong(song);
	}

	private void startSong(SongFile song) {

		if(currentPlugin != null) {
			currentPlugin.unload();
		}
		currentPlugin = null;
		playPosOffset = 0;
		songEnded = false;

		boolean flush = true; // (currentState != State.SWITCHING);

		currentState = State.SWITCHING;

		// SongFile sf = new SongFile(songName);

		List<DroidSoundPlugin> list = new ArrayList<DroidSoundPlugin>();

		for(DroidSoundPlugin plugin : plugins) {
			if(plugin.canHandle(song.getName())) {
				list.add(plugin);
				Log.v(TAG, String.format("%s handled by %s", song.getName(), plugin.getClass().getSimpleName()));
			}
		}

		byte[] songBuffer = null;
		long fileSize = 0;
		long fileSize2 = 0;

		File songFile = null;
		File songFile2 = null;
		String streamName = null;

		String baseName = song.getName();

		try {

			if(song.getZipPath() != null) {

				Log.v(TAG, "ZIP FILE");

				File f = new File(song.getZipPath());
	
				if(currentZipFile != null && f.equals(currentZipFile)) {
				} else {
					if(currentZip != null) {
						currentZip.close();
					}
					currentZipFile = f;
					currentZip = new NativeZipFile(f);
				}

				String name = song.getZipName(); // songName.substring(zipExt+5);

				Log.v(TAG, String.format("Trying to open '%s' in zipfile '%s'\n", name, f.getPath()));

				if(currentZip != null) {

					String name2 = DroidSoundPlugin.getSecondaryFile(name);

					if(name2 != null) {

						Log.v(TAG, String.format("Got secondary file '%s'\n", name2));

						ZipEntry entry = currentZip.getEntry(name);
						if(entry != null) {
							InputStream fs = currentZip.getInputStream(entry);
							songFile = writeFile(name, fs, true);

							Log.v(TAG, String.format("Wrote '%s'\n", songFile.getPath()));

							fileSize = songFile.length();
							fs.close();

							String fname2 = DroidSoundPlugin.getSecondaryFile(songFile.getPath());

							Log.v(TAG, String.format("Writing '%s'\n", fname2));

							entry = currentZip.getEntry(name2);

							if(entry != null && fname2 != null) {
								fs = currentZip.getInputStream(entry);
								songFile2 = writeFile(fname2, fs, false);
								fs.close();
								fileSize2 = songFile2.length();
							}
						}

					} else {

						Log.v(TAG, "ENTRY");
						ZipEntry entry = currentZip.getEntry(name);
						if(entry != null) {
							Log.v(TAG, String.format("Entry  '%s' %d\n", entry.getName(), entry.getSize()));
							InputStream fs = currentZip.getInputStream(entry);
							fileSize = entry.getSize();
							songBuffer = new byte[(int) fileSize];
							fs.read(songBuffer);
							fs.close();
						}
					}
				}
			} else if(song.getPath().startsWith("http://")) {
				streamName = song.getPath();
			} else {
				songFile = song.getFile(); // new File(songName);

				Log.v(TAG, "Trying to read normal file " + songFile.getPath());

				fileSize = songFile.length();
				System.gc();

				if(!songFile.exists())
					songFile = null;
				else {

					String fname2 = DroidSoundPlugin.getSecondaryFile(songFile.getPath());
					if(fname2 != null) {
						File f2 = new File(fname2);
						if(f2.exists()) {
							fileSize2 = f2.length();
						}
					}
				}
			}

		} catch (IOException e) {
			Log.w(TAG, "Could not load music!");
			e.printStackTrace();
		}

		if(songBuffer != null || songFile != null || streamName != null) {
			boolean songLoaded = false;
			for(DroidSoundPlugin plugin : list) {
				Log.v(TAG, "Trying " + plugin.getClass().getName());
				if(streamName != null) {
					try {
						songLoaded = plugin.loadStream(streamName);
						fileSize = plugin.getStreamSize();
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else if(songFile != null) {
					try {
						songLoaded = plugin.load(songFile);
					} catch (IOException e) {
					}
				} else if(songBuffer != null) {
					songLoaded = plugin.load(baseName, songBuffer, (int) fileSize);
					if(songLoaded)
						plugin.calcMD5(songBuffer, (int) fileSize);
				}
				if(songLoaded) {
					if(currentPlugin != null && currentPlugin != plugin) {
						currentPlugin.close();
					}
					currentPlugin = plugin;
					break;
				}
			}

			if(currentPlugin == null) {
				for(DroidSoundPlugin plugin : plugins) {
					if(!plugin.canHandle(song.getName())) {
						/* if(streamName != null) {
							try {
								songLoaded = plugin.loadStream(streamName);
								fileSize = plugin.getStreamSize();
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else  */ if(songFile != null) {
							try {
								songLoaded = plugin.load(songFile);
							} catch (IOException e) {
							}
						} else if(songBuffer != null) {
							songLoaded = plugin.load(baseName, songBuffer, (int) fileSize);
							if(songLoaded)
								plugin.calcMD5(songBuffer, (int) fileSize);

						}
						Log.v(TAG, String.format("%s gave Songref %s", plugin.getClass().getName(), songLoaded ? "TRUE" : "FALSE"));
						if(songLoaded) {
							currentPlugin = plugin;
							break;
						}
					}
				}
			}
		}

		if(currentPlugin != null) {
			Log.v(TAG, "HERE WE GO:" + currentPlugin.getClass().getName());

			Log.v(TAG, String.format("'%s' by '%s'", song.getTitle(), song.getComposer()));


			synchronized (this) {
				currentSong.md5 = currentPlugin.getMD5();
				currentSong.fileName = song.getPath(); // songName;
				

				if(song.getTitle() != null) {
					currentSong.title = song.getTitle();
				} else {
					currentSong.title = getPluginInfo(DroidSoundPlugin.INFO_TITLE);
				}

				if(song.getComposer() != null) {
					currentSong.author = song.getComposer();
				} else {
					currentSong.author = getPluginInfo(DroidSoundPlugin.INFO_AUTHOR);
				}

				currentSong.copyright = getPluginInfo(DroidSoundPlugin.INFO_COPYRIGHT);
				currentSong.type = getPluginInfo(DroidSoundPlugin.INFO_TYPE);
				// currentSong.game = getPluginInfo(DroidSoundPlugin.INFO_GAME);
				currentSong.subTunes = currentPlugin.getIntInfo(DroidSoundPlugin.INFO_SUBTUNE_COUNT);
				currentSong.startTune = currentPlugin.getIntInfo(DroidSoundPlugin.INFO_STARTTUNE);
				String[] info = currentPlugin.getDetailedInfo();
				if(info == null) {
					info = new String[0];
				}

				currentSong.canSeek = currentPlugin.canSeek();

				currentSong.details = new String[info.length + 2];
				for(int i = 0; i < info.length; i++) {
					currentSong.details[i] = info[i];
				}
				currentSong.details[info.length] = "Size";

				String size = makeSize(fileSize);
				if(fileSize2 > 0) {
					size = size + " + " + makeSize(fileSize2);
				}
				currentSong.details[info.length + 1] = size;
				info = null;

				currentSong.length = currentPlugin.getIntInfo(DroidSoundPlugin.INFO_LENGTH);

				if(currentSong.title == null || currentSong.title.equals("")) {

					if(currentPlugin.delayedInfo()) {
						currentSong.title = null;
					} else {					
						String basename = currentPlugin.getBaseName(currentSong.fileName);
						
						
						int sep = basename.indexOf(" - ");
						if(sep > 0) {
							currentSong.author = basename.substring(0, sep);
							currentSong.title = basename.substring(sep+3);
						} else 
							currentSong.title = basename;
						Log.v(TAG, String.format("FN Title '%s'", currentSong.title));
					}
				}

				currentSong.subtuneTitle = getPluginInfo(DroidSoundPlugin.INFO_SUBTUNE_TITLE);
				currentSong.subtuneAuthor = getPluginInfo(DroidSoundPlugin.INFO_SUBTUNE_AUTHOR);

				if(currentSong.subTunes == -1)
					currentSong.subTunes = 0;
			}

			startedFromSub = false;

			if(song.getSubtune() >= 0) {
				currentSong.startTune = song.getSubtune();
				currentSong.subTunes = 0;
				currentPlugin.setTune(song.getSubtune());
				currentTune = song.getSubtune();
				currentSong.length = currentPlugin.getIntInfo(DroidSoundPlugin.INFO_LENGTH);
				currentSong.subtuneTitle = getPluginInfo(DroidSoundPlugin.INFO_SUBTUNE_TITLE);
				currentSong.subtuneAuthor = getPluginInfo(DroidSoundPlugin.INFO_SUBTUNE_AUTHOR);
				startedFromSub = true;
			}

			lastLatency = 0;// currentSong.length;
			//lastSubtuneTitle = null;
			//lastTitle = null;

			currentSong.source = "";
			firstData = true;

			Log.v(TAG, String.format(":%s:%s:%s:%s:", currentSong.title, currentSong.author, currentSong.copyright, currentSong.type));

			Message msg = mHandler.obtainMessage(MSG_NEWSONG);

			MediaPlayer mp = currentPlugin.getMediaPlayer();
			if(mp != null) {
				currentState = State.PLAYING;
				lastPos = -1000;
				currentSong.source = currentPlugin.getStringInfo(102);
				Log.v(TAG, "MP3 SOURCE IS " + currentSong.source);

				mHandler.sendMessage(msg);

				mp.start();
				return;
			}

			mHandler.sendMessage(msg);

			if(flush) {
				audioTrack.stop();
				audioTrack.flush();
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// audioTrack.stop();
				// audioTrack.flush();

				Log.v(TAG, "START, pos " + audioTrack.getPlaybackHeadPosition());
			}

			if(reinitAudio) {
				audioTrack.release();
				audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, FREQ, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
						AudioFormat.ENCODING_PCM_16BIT, bufSize, AudioTrack.MODE_STREAM);
				samples = new short[bufSize / 2];
				reinitAudio = false;
			}

			audioTrack.play();
			currentState = State.PLAYING;
			// currentPosition = 0;
			lastPos = -1000;
			if(songFile2 != null) {
				Log.v(TAG, String.format("Deleting temporary files %s and %s", songFile.getPath(), songFile2.getPath()));

				if(songFile2 != songFile)
					songFile2.delete();
				songFile.delete();
				songFile2 = null;
			}
			return;
		}
		currentState = State.STOPPED;
	}

	private String getPluginInfo(int info) {
		String s = currentPlugin.getStringInfo(info);
		if(s == null) {
			s = "";
		}
		return s;
	}

	@Override
	public void run() {

		currentPlugin = null;

		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, FREQ, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
				AudioFormat.ENCODING_PCM_16BIT, bufSize, AudioTrack.MODE_STREAM);
		reinitAudio = false;
		Log.v(TAG, "AudioTrack created in thread " + Thread.currentThread().getId());
		noPlayWait = 0;
		int pos = 0;
		while(noPlayWait < 50) {
			try {
				if(command != Command.NO_COMMAND) {

					Log.v(TAG, String.format("Command %s while in state %s", command.toString(), currentState.toString()));

					synchronized (cmdLock) {
						switch(command) {
						case PLAY_MULTIPLE:
							SongFile [] songs = (SongFile[]) argument;							
							Log.v(TAG, "Playmod " + songs[0].getName());
							startSong(songs[0], songs[1]);
							break;
						case PLAY:
							SongFile song = (SongFile) argument;
							Log.v(TAG, "Playmod " + song.getName());
							startSong(song);
							break;
						case STOP:
							if(currentState != State.STOPPED) {
								// audioTrack.pause();
								Log.v(TAG, "STOP");
								MediaPlayer mp = currentPlugin == null ? null : currentPlugin.getMediaPlayer();
								if(mp != null) {
									mp.stop();
								} else {

									audioTrack.stop();
									audioTrack.flush();

									if(reinitAudio) {
										audioTrack.release();
										audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, FREQ,
												AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufSize,
												AudioTrack.MODE_STREAM);
										samples = new short[bufSize / 2];
										reinitAudio = false;
									}
								}

								currentPlugin.unload();
								currentPlugin = null;
								currentState = State.STOPPED;
								Message msg = mHandler.obtainMessage(MSG_STATE, 0, 0);
								mHandler.sendMessage(msg);
							}
							break;
						}
						if(currentState != State.STOPPED) {
							switch(command) {
							case SET_POS:
								int msec = (Integer) argument;
								if(currentState == State.SWITCHING) {
									currentState = State.PLAYING;
								}
								if(currentPlugin.seekTo(msec)) {

									//
									int playPos = audioTrack.getPlaybackHeadPosition() * 10 / (FREQ / 100);
									Log.v(TAG, String.format("Offset %d/1000 - %d", msec, playPos));
									playPosOffset = msec - playPos;

									// currentPosition = msec;
									// lastPos = -1000;
								}
								break;
							case SET_TUNE:
								Log.v(TAG, "Setting tune");
								if(currentPlugin.setTune((Integer) argument)) {
									playPosOffset = 0;
									// currentPosition = 0;
									currentTune = (Integer) argument;
									lastPos = -1000;
									// Log.v(TAG, "TUNE, pos " +
									// audioTrack.getPlaybackHeadPosition());
									audioTrack.pause();
									audioTrack.flush();
									try {
										Thread.sleep(100);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
									// Log.v(TAG, "TUNE, pos " +
									// audioTrack.getPlaybackHeadPosition());

									if(reinitAudio) {
										audioTrack.release();
										audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, FREQ,
												AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufSize,
												AudioTrack.MODE_STREAM);
										samples = new short[bufSize / 2];
										reinitAudio = false;
									}

									if(currentState == State.SWITCHING) {
										currentState = State.PLAYING;
									}

									// int pos =
									// audioTrack.getPlaybackHeadPosition();
									currentSong.length = currentPlugin.getIntInfo(DroidSoundPlugin.INFO_LENGTH);
									currentSong.subtuneTitle = currentPlugin.getStringInfo(DroidSoundPlugin.INFO_SUBTUNE_TITLE);
									currentSong.subtuneAuthor = getPluginInfo(DroidSoundPlugin.INFO_SUBTUNE_AUTHOR);
									Message msg = mHandler.obtainMessage(MSG_SUBTUNE, (Integer) argument, currentSong.length, new String[] {
											currentSong.subtuneTitle, currentSong.subtuneAuthor });
									mHandler.sendMessage(msg);
									audioTrack.play();
								}
								break;
							case PAUSE:
								if(currentState == State.PLAYING) {
									currentState = State.PAUSED;
									Message msg = mHandler.obtainMessage(MSG_STATE, 2, 0);
									mHandler.sendMessage(msg);
									MediaPlayer mp = currentPlugin == null ? null : currentPlugin.getMediaPlayer();
									if(mp != null)
										mp.pause();
									else
										audioTrack.pause();
								}

								break;
							case UNPAUSE:
								if(currentState == State.PAUSED) {
									currentState = State.PLAYING;
									Message msg = mHandler.obtainMessage(MSG_STATE, 1, 0);
									mHandler.sendMessage(msg);
									MediaPlayer mp = currentPlugin == null ? null : currentPlugin.getMediaPlayer();
									if(mp != null)
										mp.start();
									else
										audioTrack.play();
								}

								break;
							}
						}
						command = Command.NO_COMMAND;
					}
				}

				/*
				 * Always feed track - play zeroes after song ends Report song
				 * end at correct time
				 */

				if(currentState == State.PLAYING) {
					// Log.v(TAG, "Get sound data");
					int len = 0;
					MediaPlayer mp = currentPlugin == null ? null : currentPlugin.getMediaPlayer();
					if(mp != null) {

						int playPos = currentPlugin.getSoundData(null, 0);
						
						if(playPos < -0) { // !mp.isPlaying()) {
							// songEnded = true;
							currentState = State.SWITCHING;
							Message msg = mHandler.obtainMessage(MSG_DONE);
							mHandler.sendMessage(msg);
						}

						if(playPos > 0 && firstData) {
							

							if(currentSong.title == null)
								currentSong.title = getPluginInfo(DroidSoundPlugin.INFO_TITLE);
							if(currentSong.author == null)
								currentSong.author = getPluginInfo(DroidSoundPlugin.INFO_AUTHOR);
							
							if(currentSong.title == null || currentSong.title.length() == 0) {
								String basename = currentPlugin.getBaseName(currentSong.fileName);									
								int sep = basename.indexOf(" - ");
								if(sep > 0) {
									currentSong.author = basename.substring(0, sep);
									currentSong.title = basename.substring(sep+3);
								} else 
									currentSong.title = basename;
								Log.v(TAG, String.format("FN Title '%s'", currentSong.title));
								Message msg = mHandler.obtainMessage(MSG_INFO, 0, 0, new String[] { currentSong.title, currentSong.author });
								mHandler.sendMessage(msg);
							}
							
							String[] info = currentPlugin.getDetailedInfo();
							if(info != null) {
								Log.v(TAG, "########################################### GOT DETAILS");
								currentSong.details = info;
								Message msg = mHandler.obtainMessage(MSG_DETAILS, info);
								mHandler.sendMessage(msg);
							}
							firstData = false;
						}
						
						// int playPos = mp.getCurrentPosition();

						int latency = currentPlugin.getIntInfo(203);
						if(latency >= 0) {
							int diff = latency - lastLatency;
							if(diff < 0)
								diff = -diff;
							if(diff > 1000) {
								Message msg = mHandler.obtainMessage(MSG_PROGRESS, -1, latency);
								mHandler.sendMessage(msg);
								lastLatency = latency;
							}
						}

						//int length = currentPlugin.getIntInfo(DroidSoundPlugin.INFO_LENGTH);

						if(currentPlugin.getIntInfo(DroidSoundPlugin.INFO_DETAILS_CHANGED) != 0) {

							String title = currentPlugin.getStringInfo(DroidSoundPlugin.INFO_TITLE);
							String author = currentPlugin.getStringInfo(DroidSoundPlugin.INFO_AUTHOR);

							String subTuneTitle = currentPlugin.getStringInfo(DroidSoundPlugin.INFO_SUBTUNE_TITLE);
							String subTuneAuthor = currentPlugin.getStringInfo(DroidSoundPlugin.INFO_SUBTUNE_AUTHOR);
							int length = currentPlugin.getIntInfo(DroidSoundPlugin.INFO_LENGTH);
							
							Log.v(TAG, "######## GOT NEW META INFO");
							Message msg;
							if(subTuneTitle != null && (!subTuneTitle.equals(currentSong.subtuneTitle))) {
								msg = mHandler.obtainMessage(MSG_SUBTUNE, -1, length, new String[] { subTuneTitle, subTuneAuthor });
								currentSong.subtuneTitle =  subTuneTitle;
							} else {
								msg = mHandler.obtainMessage(MSG_SUBTUNE, -1, length, null);
							}
							mHandler.sendMessage(msg);							
							
							if(title != null && (!title.equals(currentSong.title))) {
								msg = mHandler.obtainMessage(MSG_INFO, 0, 0, new String[] { title, author });
								mHandler.sendMessage(msg);
								currentSong.title = title;
							}
							
						}

						int song = currentPlugin.getIntInfo(DroidSoundPlugin.INFO_SUBTUNE_NO);
						if(song >= 0 && song != currentTune) {

							if(startedFromSub) {
								currentState = State.SWITCHING;
								Message msg = mHandler.obtainMessage(MSG_DONE);
								mHandler.sendMessage(msg);
								startedFromSub = false;
							}

							currentTune = song;
							// currentSong.length =
							// currentPlugin.getIntInfo(DroidSoundPlugin.INFO_LENGTH);
							currentSong.subtuneTitle = currentPlugin.getStringInfo(DroidSoundPlugin.INFO_SUBTUNE_TITLE);
							currentSong.subtuneAuthor = getPluginInfo(DroidSoundPlugin.INFO_SUBTUNE_AUTHOR);
							Message msg = mHandler.obtainMessage(MSG_SUBTUNE, currentTune, currentSong.length, new String[] {
									currentSong.subtuneTitle, currentSong.subtuneAuthor });
							mHandler.sendMessage(msg);
						}

						Message msg = mHandler.obtainMessage(MSG_PROGRESS, playPos, -1);
						mHandler.sendMessage(msg);
						Thread.sleep(100);
						continue;
					}

					if(!songEnded) {
						//Log.v(TAG, "Get sound data");
						len = currentPlugin.getSoundData(samples, bufSize / 16);
						//Log.v(TAG, "DONE");
					} else {
						Thread.sleep(100);
					}

					int p = audioTrack.getPlaybackHeadPosition();

					// currentPosition += ((len * 1000) / (FREQ*2));
					// Log.v(TAG, "pos " + currentPosition);

					// Log.v(TAG, String.format("%d vs %d", pos, p));
					if(songEnded && p == pos) {
						lastTime = -1;
						currentState = State.SWITCHING;
						Message msg = mHandler.obtainMessage(MSG_DONE);
						mHandler.sendMessage(msg);
					}

					pos = p;
					int playPos = pos * 10 / (FREQ / 100);

					if(pos >= lastPos + FREQ / 2) {
						// Log.v(TAG,
						// String.format("PLAY %d sec %d pos = %d msec ",
						// currentPosition, pos, pos * 1000 / 44100));

						if(aCount == 0)
							aCount = 1;
						// cpu = 100 - (int) (audioTime / aCount);
						Message msg = mHandler.obtainMessage(MSG_PROGRESS, playPos + playPosOffset, -1);
						aCount = 0;
						audioTime = 0;
						mHandler.sendMessage(msg);
						lastPos = pos;

						if(currentPlugin.isSilent()) {
							if(silentPosition < 0) {
								silentPosition = pos;
							} else if(pos > silentPosition + 2500) {
								msg = mHandler.obtainMessage(MSG_SILENT);
								mHandler.sendMessage(msg);
								Log.v(TAG, "Silence");
								silentPosition = -1;
							}
						} else {
							silentPosition = -1;
						}

					}
					// Log.v(TAG, "write " + len);

					if(len < 0) {
						if(playPos > 5000) {
							songEnded = true;
						}
						len = bufSize / 16;
						Arrays.fill(samples, 0, len, (short) 0);
					}
					if(len > 0) {
						long t = System.currentTimeMillis();
						audioTrack.write(samples, 0, len);

						long tt = System.currentTimeMillis();
						if(lastTime > 0) {
							frameTime = (tt - lastTime);
							long d = tt - t;
							// Log.v(TAG, String.format("Frame %d, write %d",
							// frameTime, d));
							if(frameTime > 0) {
								audioTime += ((d * 100) / frameTime);
								aCount++;
							}
						}
						lastTime = tt;
					}

					// Log.v(TAG, "loop");
					noPlayWait = 0;
				} else {
					if(currentState == State.PAUSED && currentPlugin.getMediaPlayer() != null) {
						int latency = currentPlugin.getIntInfo(203);
						if(latency >= 0) {
							if(latency / 1000 != lastLatency / 1000) {
								Message msg = mHandler.obtainMessage(MSG_PROGRESS, -1, latency);
								mHandler.sendMessage(msg);
								lastLatency = latency;
							}
						}
					}

					if(currentState == State.STOPPED) {
						noPlayWait++;
					}
					Thread.sleep(100);
				}

			} catch (InterruptedException e) {
				break;
			}
		}

		audioTrack.release();
		audioTrack = null;

		for(DroidSoundPlugin plugin : plugins) {
			plugin.exit();
		}

		Log.v(TAG, "Player Thread exiting due to inactivity");

	}

	synchronized int getPosition() {
		return audioTrack.getPlaybackHeadPosition();
	}

	synchronized public boolean getSongInfo(SongInfo target) {
		target.title = currentSong.title == null ? null : new String(currentSong.title);
		target.author = currentSong.author == null ? null : new String(currentSong.author);
		target.copyright = currentSong.copyright == null ? null : new String(currentSong.copyright);
		// target.game = new String(currentSong.game);
		target.type = currentSong.type == null ? null : new String(currentSong.type);
		target.length = currentSong.length;
		target.subTunes = currentSong.subTunes;
		target.startTune = currentSong.startTune;
		target.details = currentSong.details;
		target.subtuneTitle = currentSong.subtuneTitle;
		target.subtuneAuthor = currentSong.subtuneAuthor;
		target.fileName = currentSong.fileName;
		target.canSeek = currentSong.canSeek;
		target.source = currentSong.source;
		target.md5 = currentSong.md5;
		return true;
	}

	public void stop() {
		synchronized (cmdLock) {
			command = Command.STOP;
		}
	}

	public void paused(boolean pause) {
		synchronized (cmdLock) {
			if(pause) {
				Log.v(TAG, "Pausing");
				command = Command.PAUSE;
			} else {
				Log.v(TAG, "Unpausing");
				command = Command.UNPAUSE;
			}
		}
	}

	public void seekTo(int pos) {
		synchronized (cmdLock) {
			command = Command.SET_POS;
			argument = pos;
		}
	}

	public void playMod(SongFile mod) {
		synchronized (cmdLock) {
			command = Command.PLAY;
			argument = mod;
		}
	}
	public void playMod(SongFile mod, SongFile nextMod) {
		synchronized (cmdLock) {
			command = Command.PLAY_MULTIPLE;
			argument = new SongFile[] { mod, nextMod };
		}
	}

	public void setSubSong(int song) {
		synchronized (cmdLock) {
			command = Command.SET_TUNE;
			argument = song;
		}
	}

	public boolean isActive() {
		return currentState != State.STOPPED;
	}

	public boolean isPlaying() {
		return (currentState == State.PLAYING);
	}

	public boolean isSwitching() {
		return (currentState == State.SWITCHING);
	}

	public void setBufSize(int bs) {
		synchronized (cmdLock) {
			if(bufSize != bs) {
				bufSize = bs;
				Log.v(TAG, "Buffersize now " + bs);
				reinitAudio = true;
			}
		}
	}

}
