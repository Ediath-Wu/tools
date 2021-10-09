package com.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;

public class Crontab {
	private String ROOT = new File(this.getClass().getResource("../../").getPath()).getParent() 
			+ File.separator; // ��Ŀ��Ŀ¼
	
	
	// һЩĬ�ϵ�����
	private String FILE_CFG = ROOT + "conf/config.properties";
	private String FILE_ERR = "log/crontab.err";
	private String FILE_LOG = "log/crontab.log";
	
	private Properties prop;
	
	private List<String> FILE_SCHS ;
	private List<String> DIR_SCRIPTS ;
	private Boolean SYS_CHECK_UPDATE = true;
	private Boolean SYS_SEND_POWER_DATA = true;
	private Boolean SYS_SEND_ERROR_DATA = true;
	private Integer START_SCHEDULE_AFTER_MINUTE = 5;
	
	private List<Task> tasks;	// ��������
	
	private String CACHE_DIR;
	private String FILE_RUN = "run.obj";	// ����ļ��ŵ� cache ��ȥ���������û��Ӵ�
	private Map<String, Long> runObj; // ���д���[count]/�ػ�ʱ��[downtime]/����ʱ��[uptime]
	private Map<String, String> scriptsMap;
	
	public Crontab () throws Exception {
		
		// ��ȡ�����ļ�
		try {
			this.getConfigsFromFile(this.FILE_CFG);
		} catch (Exception e) {
			this.writeLog("��ȡ�����ļ�[ " + this.FILE_CFG + " ]ʧ�ܣ�\r\nԭ��: " + e.getMessage(), true);
		}
		
		// ��ȡ�����ļ��еĽű�Ŀ¼�µ����еĽű��ļ�
		this.scriptsMap = this.getScripts();
		
		// �����ļ��� runObj ��Ҫ�����ڻ����ļ�
		try {
			this.CACHE_DIR = Helper.getCacheDir(ROOT + ".cache");
		} catch (Exception e) {
			this.writeLog("���������ļ�ʧ�ܣ�ԭ�� " + e.getMessage(), true);
			throw new Exception("�ƻ���������ʧ�ܣ���鿴������־�ļ�");
		}
		// �������ļ����뻺���ļ���
		this.FILE_RUN = this.CACHE_DIR + File.separator + this.FILE_RUN;
		
		// ��ȡ�ƻ�����
		this.tasks = this.getTasksFromFiles(this.FILE_SCHS);
		
		// ��ȡ���д���/�ػ�ʱ��/����ʱ��
		this.runObj = this.getRunObj();
	}
	
	/**
	 * ��ȡ�ű��ļ����ڵ����нű��ļ�
	 * @return
	 */
	private Map<String, String> getScripts() {
		File f;
		String[] scripts;
		Map<String, String> scriptsMap = new HashMap<String, String>();
		for (String dir : this.DIR_SCRIPTS) {
			f = new File(dir);
			if (f.exists() && f.isDirectory()) {
				for (String script : f.list()) {
					scriptsMap.put(script, dir + File.separator + script);
				}
				
			}
		}
		return scriptsMap;
	}

	/**
	 * ����ʱ�����ݣ��������д���/�ϴιػ�ʱ��/֮ǰ����ʱ��
	 * @return
	 */
	private Map<String, Long> getRunObj() {
		HashMap<String, Long> lastObj = null, 
				obj = new HashMap<String, Long>();
		
		File runFile = new File(this.FILE_RUN);
		long lastModify = 0, currTime = new Date().getTime();
		
		if (runFile.exists()) {
			lastModify = runFile.lastModified();
			try {
				lastObj = Helper.readObjectFile(this.FILE_RUN);	
			} catch (Exception e) {
				this.writeLog("��ȡ����ʱ����ʧ�ܣ�ԭ�� " + e.getMessage(), true);
			}
			runFile.delete();
		}
		
		if (lastObj != null) {
			// ������� 10 ���������û��ػ��ˣ����ǿ�����ĵ�һ������
			if (currTime - lastModify >= 10 * 60 * 1000) {
				obj.put("count", (long) 1);
				obj.put("downtime", lastModify);
				obj.put("uptime", currTime);
			} else {
				lastObj.put("count", lastObj.get("count") + 1);
				obj = lastObj;
			}
		
		// û�����й�
		} else {
			obj.put("count", (long) 1);
			obj.put("downtime", (long) 0);
			obj.put("uptime", currTime);
		}
		try {
			Helper.writeObjectFile(this.FILE_RUN, obj);
		} catch (Exception e) {
			this.writeLog("д������ʱ����ʧ�ܣ�ԭ�� " + e.getMessage(), true);
		}
		return obj;
	}

	// ���ļ��з����� Task ��
	private List<Task> getTasksFromFiles(List<String> files) {
		List<Task> tasks = new ArrayList<Task>();
		String[] lines;
		Task task;
		
		for (String file : files) {
			try {
				lines = Helper.readFile(file).split(Helper.LineSep);
				for (String line : lines) {
					line = line.trim();
					// ���� TASK�����ƥ���˵�ǰʱ�䣬��ִ�� TASK
					if (line.length() > 0 && line.charAt(0) != '#') {
						// ��ÿһ���е� $1 �ַ��滻�� ��ǰjava�ļ���·��
						line = line.replaceAll(Matcher.quoteReplacement(" $1" + File.separator), 
								Matcher.quoteReplacement(" " + ROOT));
						try {
							task = new Task(line);
							
							// ���� CMD �е� script
							String script, ext, scriptExt;
							for (Integer i = 0; i < task.cmds.length; i++) {
								script = task.cmds[i].split("\\s+")[0];
								if (scriptsMap.containsKey(script)) {
									ext = script.substring(script.lastIndexOf('.') + 1);
									scriptExt = prop.getProperty("script." + ext);
									if (scriptExt != null) {
										task.cmds[i] = task.cmds[i].replace(script, scriptExt.replaceAll(Matcher.quoteReplacement("$1"), 
												Matcher.quoteReplacement(scriptsMap.get(script))));
									}
								}
							}
							
							tasks.add(task);
							
						} catch (Exception e) {
							String msg = "�����ƻ�����ʧ�ܣ�ԭ�� " + e.getMessage();
							this.writeLog(msg, true);
						}
					}
				}
			} catch (Exception e) {
				this.writeLog("��ȡ�ƻ������ļ�ʧ�ܣ�ԭ�� " + e.getMessage(), true);
			}
				
		}
		
		return tasks;
	}
	
	
	public void listTasks() {
		Task task;
		Map<String, Integer> timeMap;
		String next;
		for (Integer i = 0; i < this.tasks.size(); i++) {
			task = this.tasks.get(i);
			try {
				timeMap = Helper.justifyCalendar(task.getNextRunTime());
				next = timeMap.get("year") + "/" + timeMap.get("month") + "/" + timeMap.get("day") 
						+ " " + timeMap.get("hour") + ":" + timeMap.get("minute");
			} catch( Exception e ) {
				next = "δ֪";
			}
			System.out.println("--------------------------------------------------");
			System.out.println("ID: " + i + "   Next Run Time: " + next);
			System.out.println(task.toString());
			System.out.println();
		}
	}
	
	/**
	 * ��������һ�����񣬲�������ʱ������
	 * @param task
	 */
	public void runTask(Integer id) {
		if (this.tasks.size() <= id || id < 0) {
			System.out.println("ָ��������[ID="+id+"]������");
		} else {
			Task task = this.tasks.get(id);
			try {
				String output = "";
				for (String cmd : task.cmds) {
					output = Helper.execCMD(cmd) + Helper.LineSep;
				}
				System.out.print("Output: ");
				System.out.println(output);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * �������е�����
	 */
	public void run() {
		long runCount = this.runObj.get("count");
		Boolean isStartup = false; // �Ƿ��Ǹտ���
		
		// �տ����ļ������ڲ�ִ����������
		if (runCount > this.START_SCHEDULE_AFTER_MINUTE) {
			if (runCount == this.START_SCHEDULE_AFTER_MINUTE + 1) isStartup = true;
			
			long currTime = new Date().getTime();
			String output;
			Boolean canRun;
			for (Task task : this.tasks) {

				canRun = (task.canRunAt(currTime) || isStartup && task.isStartUpTask) ? true : false;
				
				if (!canRun && isStartup && task.isPowerOffTask && this.runObj.get("downtime") != 0) {
					// �ϴιػ��� -> ���ο���֮�䣬��������Ƿ�Ӧ�ñ�ִ��
					try {
						if (task.getNextRunTime(this.runObj.get("downtime")) < currTime) {
							canRun = true;
						}
					} catch (Exception e) {	} // û�л�ȡ���´�����ʱ�䣬��������
				}
			
				if (canRun) {
					try {
						output = task.run(currTime);
						this.writeLog(task.toString() + Helper.LineSep + "Output: " + output, false);
					} catch (Exception e) {
						String msg = "��������[" + task.toString() + "]ʧ�ܣ�ԭ�� " + e.getMessage();
						this.writeLog(msg, true);
						if (task.alert >= 0) {
							try {
								this.alert(msg, task.alert);
							} catch (Exception e1) {
								this.writeLog(e.getMessage(), true);
							}
						}
					}
				}
			}
		}
		
	}
	
	
	public static void main(String[] args) {
		Crontab cron = null;

		if (args.length == 0 || !args[0].equals("-l") && !args[0].equals("-r")) {
			System.out.println("  Usage:");
			System.out.println("   crontab -l �г���ǰ���еļƻ�����");
			System.out.println("   crontab -r [id] ����ָ���� id �ļƻ����� id ����ͨ�� crontab -l �鿴");
			System.exit(0);
		}
		
		try {
			cron = new Crontab();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
		
		String arg;
		arg = args[0];
		if (arg.equals("-l")) {
			cron.listTasks();
		} else if (arg.equals("-r")) {
			if (args.length < 2) {
				cron.run();
			} else {
				try {
					Integer id = Integer.parseInt(args[1]);
					cron.runTask(id);
				} catch (Exception e) {
					System.out.println("ָ����ID["+args[1]+"]��Ϊ����");
				}
			}
		}
		System.exit(0);
	}

	/**
	 * ��־�ļ�д��ʧ�ܾͲ����������ˣ�������ѭ��
	 * ������Ҫ�û��Լ�ȷ�������õ���־�ļ����ǿ�д��
	 */
	private void writeLog(String msg, Boolean isError) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		Map<String,Integer> timeMap = Helper.justifyCalendar(cal.getTimeInMillis());
		
		StringBuilder sb = new StringBuilder();
		
		sb.append(timeMap.get("year") + "/" + timeMap.get("month") + "/" + timeMap.get("day") 
				+ " " + timeMap.get("hour") + ":" + timeMap.get("minute"));
		sb.append(Helper.LineSep);
		sb.append("-----------------------------------------------------------------------");
		sb.append(Helper.LineSep);
		msg = "  " + msg;
		sb.append(msg.replaceAll(Helper.LineSep, Helper.LineSep + "  "));
		sb.append(Helper.LineSep);
		sb.append(Helper.LineSep);
		sb.append(Helper.LineSep);
		
		String path = isError ? this.FILE_ERR : this.FILE_LOG;
		try {
			System.out.println(sb.toString());
			Helper.writeFile(path, sb.toString(), true);
		} catch (Exception e) {
			System.out.println("д����־�ļ�[ " + path + " ]ʧ��");
			System.out.println("Error: " + e.getMessage());
		}
	}
	
	
	/**
	 * ��ȡ�����ļ�������֤��Ϣ׼��
	 * @throws Exception
	 */
	private void getConfigsFromFile(String file) throws Exception {
		FileInputStream fis;
		prop = new Properties();
		try {
			fis = new FileInputStream(file);
			prop.load(fis);
			fis.close();
		} catch (IOException e) {
			throw new Exception("��ȡ�����ļ�[ " + file + " ]ʧ��");
		}
		
		String val;
		Boolean boolVal;
		String[] propsOnOrOff = {"sys.check_update", "sys.send_power_data", "sys.send_error_data"};
		for (String key : propsOnOrOff) {
			val = prop.getProperty(key, "on");
			boolVal = (val.equals("off") || val.equals("0") || val.equals("false")) ? false : true;
			switch (key) {
			case "sys.check_update": this.SYS_CHECK_UPDATE = boolVal; break;
			case "sys.send_power_data": this.SYS_SEND_POWER_DATA = boolVal; break;
			case "sys.send_error_data": this.SYS_SEND_ERROR_DATA = boolVal; break;
			}
		}
		
		Integer intVal;
		val = prop.getProperty("sys.start_schedule_after_minute", "5");
		try {
			intVal = Integer.parseInt(val);
		} catch (Exception e) { intVal = 5; }
		if (intVal < 0) intVal = 5;
		this.START_SCHEDULE_AFTER_MINUTE = intVal;

		
		this.FILE_ERR = prop.getProperty("file.error", this.FILE_ERR).trim();
		if (this.FILE_ERR.charAt(1) != ':') this.FILE_ERR = ROOT + this.FILE_ERR;
		this.FILE_LOG = prop.getProperty("file.log", this.FILE_LOG).trim();
		if (this.FILE_LOG.charAt(1) != ':') this.FILE_LOG = ROOT + this.FILE_LOG;
		

		String[] files = prop.getProperty("file.schedule").split(";");
		
		this.FILE_SCHS = new ArrayList<String>();
		for (String sch : files) {
			sch = sch.trim();
			if (sch.length() > 0) {
				if (sch.charAt(1) != ':') sch = ROOT + sch;
				this.FILE_SCHS.add(sch);
			}
		}
		if (this.FILE_SCHS.size() == 0) {
			this.FILE_SCHS.add(ROOT + "conf/schedule.conf");
		}
		
		files = prop.getProperty("file.scripts").split(";");
		this.DIR_SCRIPTS = new ArrayList<String>();
		for (String script : files) {
			script = script.trim();
			if (script.length() > 0) {
				if (script.charAt(1) != ':') script = ROOT + script;
				this.DIR_SCRIPTS.add(script);
			}
		}
		if (this.DIR_SCRIPTS.size() == 0) {
			this.DIR_SCRIPTS.add(ROOT + "scripts");
		}
		
		
	}
	
	
	/**
	 * ����һ�����Ƶ�CMD���ڣ������û� msg ��Ϣ
	 * @param msg
	 * @throws Exception 
	 */
	public void alert(String msg, Integer timeout) throws Exception {
		String cacheDir = this.CACHE_DIR;
		File alertFile = new File(cacheDir + File.separator + String.valueOf(Math.random()) + ".bat");
		if (alertFile.exists()) {
			alertFile.delete();
		}
		alertFile.createNewFile();
		
		String sep = Helper.LineSep;
		String strTimeout = timeout <= 0 ? "" : " /D Y /T " + timeout;
		
		StringBuilder sb = new StringBuilder();
		sb.append("@ECHO OFF" + sep);
		sb.append("" + sep);
		sb.append("cls" + sep);
		sb.append("color EC" + sep);
		sb.append("echo." + sep);
		sb.append("echo." + sep);
		sb.append("echo 	################################################" + sep);
		sb.append("echo 	##                   Alert                    ##" + sep);
		sb.append("echo 	##        %DATE% %TIME%         ##" + sep);
		sb.append("echo 	################################################" + sep);
		sb.append("echo." + sep);
		sb.append("echo	Alert: " + msg + sep);
		sb.append(":CHOSE" + sep);
		sb.append("echo." + sep);
		sb.append("CHOICE /C YN" + strTimeout + " /M \"Confirm the Alert: \"" + sep);
		sb.append("IF errorlevel 2 GOTO NO" + sep);
		sb.append("IF errorlevel 1 GOTO YES" + sep);
		sb.append(":YES" + sep);
		sb.append("GOTO END" + sep);
		sb.append(":NO" + sep);
		sb.append("GOTO CHOSE" + sep);
		sb.append(":END" + sep);
		sb.append("EXIT" + sep);
		
		
		
		try {
			Helper.writeFile(alertFile.getAbsolutePath(), sb.toString(), false);
			Helper.execCMD("cmd /c start " + alertFile.getAbsolutePath());
		} catch (Exception e) {
			throw new Exception("ִ�� alert ����ʧ��");
		}
		
		alertFile.delete();
	}

}
