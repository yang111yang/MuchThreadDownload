package com.itheima.muchthreaddown;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class MuchThreadDown {

	private static int threadCount = 3;// 开启3个线程
	private static int blockSize = 0;// 每个线程下载的大小
	private static int runningThreadCount = 0;// 当前运行的线程数
	private static String path = "";

	public static void main(String[] args) {

		try {
			// 1.请求url地址，获取资源的大小
			URL url = new URL(path);
			HttpURLConnection connection = (HttpURLConnection) url
					.openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(10 * 1000);
			int code = connection.getResponseCode();
			if (code == 200) {
				// 获取资源的大小
				int fileLength = connection.getContentLength();
				// 2.在本地创建一个与服务端资源同样大小的文件（占位）
				RandomAccessFile randomAccessFile = new RandomAccessFile(
						new File("feiq.exe"), "rw");
				randomAccessFile.setLength(fileLength);// 设置随机访问文件的大小

				// 3.要分配每个线程下载文件的开始位置和结束位置
				blockSize = fileLength / threadCount;// 计算出每个线程理论下载大小
				for (int threadId = 0; threadId < threadCount; threadId++) {
					int startIndex = threadId * blockSize;// 每个线程的开始位置
					int endIndex = (threadId + 1) * blockSize - 1;// 每个线程的结束位置
					// 如果是最后一个线程，结束位置需要单独计算
					if (threadId == threadCount - 1) {
						endIndex = fileLength - 1;
					}
					// 4.开启线程去执行下载
					new DownloadThread(threadId, startIndex, endIndex);

				}
				randomAccessFile.close();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static class DownloadThread extends Thread {

		private int threadId;
		private int startIndex;
		private int endIndex;
		private int lastPosition;

		public DownloadThread(int threadId, int startIndex, int endIndex) {
			this.threadId = threadId;
			this.startIndex = startIndex;
			this.endIndex = endIndex;
		}

		public void run() {

			synchronized (DownloadThread.class) {
				runningThreadCount++;// 开启一个线程，线程数加1
			}

			// 分段请求网络连接，分段保存文件到本地
			try {
				URL url = new URL(path);
				HttpURLConnection connection = (HttpURLConnection) url
						.openConnection();
				connection.setRequestMethod("GET");
				connection.setConnectTimeout(10 * 1000);

				// 读取上次下载结束的位置,本次从这个位置直接下载
				File file = new File(threadId + ".txt");
				if (file.exists()) {
					BufferedReader bufferedReader = new BufferedReader(
							new InputStreamReader(new FileInputStream(file)));
					String lastPosition_str = bufferedReader.readLine();
					lastPosition = Integer.parseInt(lastPosition_str);
					// 设置分段下载头信息.Range:做分段数据请求用的
					connection.setRequestProperty("Range", "bytes:"
							+ lastPosition + "-" + endIndex);
					bufferedReader.close();
				} else {
					lastPosition = startIndex;
					// 设置分段下载头信息.Range:做分段数据请求用的
					connection.setRequestProperty("Range", "bytes:"
							+ lastPosition + "-" + endIndex);

				}

				if (connection.getResponseCode() == 206) {// 200:请求全部资源成功，206:部分资源请求成功
					InputStream inputStream = connection.getInputStream();
					// 请求成功将流写入本地文件中，已经创建的占位的那个文件中
					RandomAccessFile randomAccessFile = new RandomAccessFile(
							new File("feiq.exe"), "rw");
					randomAccessFile.seek(lastPosition);// 设置随机文件从那个位置开始写
					// 将流中的数据写入文件
					byte[] buffer = new byte[1024];
					int length = -1;
					int total = 0;// 记住本次线程下载的总大小
					while ((length = inputStream.read(buffer)) != -1) {
						randomAccessFile
								.write(buffer, lastPosition + 1, length);
						total += length;
						// 保存当前线程下载的位置，保存到文件中
						int currentThreadPosition = lastPosition + total;// 计算出当前线程本次下载的位置
						RandomAccessFile accessfile = new RandomAccessFile(
								new File(threadId + ".txt"), "rwd");
						accessfile.write(String.valueOf(currentThreadPosition)
								.getBytes());
						accessfile.close();

					}
					// 关闭相关的流信息
					inputStream.close();
					randomAccessFile.close();
					
					//当所有线程下载结束，删除存放位置的文件
					synchronized (DownloadThread.class) {
						runningThreadCount--;//一个线程结束，线程数减1
						if (runningThreadCount==0) {
							for (int i = 0; i < threadCount; i++) {
								File file2 = new File(i+".txt");
								file2.delete();
							}
						}
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			super.run();
		}

	}

}
