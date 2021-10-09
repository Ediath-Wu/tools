package com.main;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Helper {

	public final static String LineSep = System.getProperty("line.separator");

	/**
	 * Ĭ�ϵ����ں����ǵ�ϰ�߲�̫һ��������ֻ��������������ת����������
	 * 
	 * ��������ֶη�Χ�ǣ� minute: 0-59 hour: 0-23 day: 1-31 month: 1-12 week: 0-6 year: ʵ�����
	 */
	public static Map<String, Integer> justifyCalendar(long timeInMillisecond) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(timeInMillisecond);
		Map<String, Integer> result = new HashMap<String, Integer>();

		result.put("minute", cal.get(Calendar.MINUTE));
		result.put("hour", cal.get(Calendar.HOUR_OF_DAY));
		result.put("day", cal.get(Calendar.DAY_OF_MONTH));
		result.put("month", cal.get(Calendar.MONTH) + 1); // һ����0�����εݼӣ�����Ҫ�� 1
		result.put("week", cal.get(Calendar.DAY_OF_WEEK) - 1); // �����졢һ���� ...
																// ������ => 1 - 7��
																// ����Ҫ�� 1
		result.put("year", cal.get(Calendar.YEAR));
		
		return result;
	}

	/**
	 * �� justifyCalendar ���ص��ֶ����»ָ��� millisecond ����ʽ ע�⣺���Բ���Ҫ week�����û������
	 * year����Ĭ��ȡ��ǰ�� year
	 * 
	 * @param mapTime
	 * @return
	 */
	public static long reverseJustifyCalendar(Map<String, Integer> mapTime) {
		Calendar cal = Calendar.getInstance();
		Integer year = mapTime.containsKey("year") ? mapTime.get("year") : cal
				.get(Calendar.YEAR);
		cal.set(year, mapTime.get("month") - 1, mapTime.get("day"),
				mapTime.get("hour"), mapTime.get("minute"), 0);
		return cal.getTimeInMillis();
	}

	/**
	 * ��ȡ�ļ�����
	 * 
	 * @param path
	 * @return
	 * @throws FileNotFoundException
	 *             , IOException
	 */
	public static String readFile(String path) throws FileNotFoundException,
			IOException {
		String line;
		StringBuilder sb = new StringBuilder();
		Boolean addLineSep = true;

		FileReader fr = new FileReader(path);
		BufferedReader bf = new BufferedReader(fr);
		
		
		// ���ж�ȡ�����ļ�
		int len;
		while ((line = bf.readLine()) != null) {
			len = line.length();
			if (len > 0 && line.charAt(len - 1) == '\\') {
				line = line.substring(0, len-1);
				addLineSep = false;
			} else {
				addLineSep = true;
			}
			sb.append(line);
			if (addLineSep) sb.append(Helper.LineSep);
		}

		bf.close();
		fr.close();

		return sb.toString();
	}
	

	/**
	 * д�������ļ�
	 * 
	 * @param path
	 * @param content
	 * @param appendToFile
	 * @throws IOException
	 */
	public static void writeFile(String path, String content,
			Boolean appendToFile) throws Exception {
		File f = new File(path);
		
		if (!f.exists()) f.createNewFile();
		if (f.isFile() && f.canWrite()) {
			// ����Ҳ���� IOException Ŷ
			FileWriter fw = new FileWriter(new File(path), appendToFile);
			fw.write(content, 0, content.length());
			fw.flush();
			fw.close();
		} else {
			throw new Exception(path + " �����ı��ļ������ļ�����д��");
		}

	}

	/**
	 * ���ļ��ж�ȡ�����л��Ķ���
	 * 
	 * @param path
	 * @return
	 * @throws ClassNotFoundException
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Serializable> T readObjectFile(String path)
			throws FileNotFoundException, IOException, ClassNotFoundException,
			Exception {

		FileInputStream fis = new FileInputStream(path);
		ObjectInputStream ois = null;

		T result;
		try {
			ois = new ObjectInputStream(fis);
			result = (T) ois.readObject();
		} catch (EOFException e) {
			// ���л�����ʧ��
			result = null;
		} finally {
			fis.close();
			if (ois != null) ois.close();
		}

		if (result == null) {
			throw new Exception(path + " �����л�����ʧ�ܡ�");
		}

		return result;
	}

	/**
	 * �ѿ����л��Ķ���д���ļ���
	 * 
	 * @param path
	 * @param object
	 * @throws Exception
	 */
	public static <T extends Serializable> void writeObjectFile(String path,
			T object) throws Exception {

		File f = new File(path);
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		
		if (!f.exists()) f.createNewFile();
		if (f.isFile() && f.canWrite()) {
			// ����Ҳ���� IOException Ŷ
			fos = new FileOutputStream(f);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(object);
			fos.close();
			oos.close();
		} else {
			throw new Exception(path + " �����ı��ļ������ļ�����д��");
		}

	}

	/**
	 * MD5 ����
	 * 
	 * @param msg
	 * @return
	 */
	public static String md5(String msg) {
		String s = null;
		byte[] source = msg.getBytes();
		char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
				'a', 'b', 'c', 'd', 'e', 'f' };
		try {
			java.security.MessageDigest md = java.security.MessageDigest
					.getInstance("MD5");
			md.update(source);
			byte tmp[] = md.digest(); // MD5 �ļ�������һ�� 128 λ�ĳ�������
			// ���ֽڱ�ʾ���� 16 ���ֽ�
			char str[] = new char[16 * 2]; // ÿ���ֽ��� 16 ���Ʊ�ʾ�Ļ���ʹ�������ַ���
			// ���Ա�ʾ�� 16 ������Ҫ 32 ���ַ�
			int k = 0; // ��ʾת������ж�Ӧ���ַ�λ��
			for (int i = 0; i < 16; i++) { // �ӵ�һ���ֽڿ�ʼ���� MD5 ��ÿһ���ֽ�
				// ת���� 16 �����ַ���ת��
				byte byte0 = tmp[i]; // ȡ�� i ���ֽ�
				str[k++] = hexDigits[byte0 >>> 4 & 0xf]; // ȡ�ֽ��и� 4 λ������ת��,
				// >>> Ϊ�߼����ƣ�������λһ������
				str[k++] = hexDigits[byte0 & 0xf]; // ȡ�ֽ��е� 4 λ������ת��
			}
			s = new String(str); // ����Ľ��ת��Ϊ�ַ���
		} catch (Exception e) {
			e.printStackTrace();
		}
		return s;
	}
	
	/**
	 * �򵥵�ִ�� cmd ����ĺ���
	 * 
	 * @return ִ�����������������ַ�
	 */
	public static String execCMD(String cmd) throws Exception {
		Runtime run = Runtime.getRuntime();

		Process process = run.exec(cmd);// ������һ��������ִ������

		BufferedInputStream bis = new BufferedInputStream(
				process.getInputStream());
		BufferedReader br = new BufferedReader(new InputStreamReader(bis));

		String line;
		StringBuilder sb = new StringBuilder();
		String lineSep = System.getProperty("line.separator");
		while ((line = br.readLine()) != null) {
			// �������ִ�к��ڿ���̨�������Ϣ
			sb.append(line);
			sb.append(lineSep);
		}

		// ��������Ƿ�ִ��ʧ��
		if (process.waitFor() != 0) {
			if (process.exitValue() != 0) // exitValue()==0��ʾ����������1������������
				throw new Exception("���� [" + cmd + "] �˳�״ֵ̬��Ϊ 0�� ִ��ʧ��!");
		}
		br.close();
		bis.close();

		return sb.toString();
	}
	
	

	
	/**
	 * ����һ�����ص� cache �ļ��У������ļ��е�ַ
	 * @throws Exception 
	 */
	public static String getCacheDir(String path) throws Exception {
		File cacheDir = new File(path);
		if ( !cacheDir.exists() || cacheDir.isFile()) {
			
			if (cacheDir.exists()) cacheDir.delete();
			
			cacheDir.mkdir();
			// ͨ��CMD�������ó�����
			Runtime.getRuntime().exec("attrib +H \"" + cacheDir.getAbsolutePath() + "\"");
		}
		
		if (!cacheDir.canWrite()) {
			throw new Exception("�޷�д�����ļ�[ " + cacheDir.getAbsolutePath() + " ]");
		}
		
		return cacheDir.getAbsolutePath();
	}
	
	
}
