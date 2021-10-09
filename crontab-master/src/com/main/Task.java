package com.main;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Task implements Serializable {
	private static final long serialVersionUID = 7165811152451154376L;

	// �ڼƻ�����ǰ���� S �ַ����Ϳ����øüƻ����񿪻�ʱһ������һ��
	public Boolean isStartUpTask = false;

	// �ڼƻ�����ǰ���� P �ַ�ʱ������������ڹػ�ʱ����ڱ�����Ҫִ�У����ڿ�����һ��ʱ���ڻ��Զ�����һ��
	public Boolean isPowerOffTask = false;
	
	// �ƻ�����ǰ���� A �ַ���������Խ�һ�����֣���ʾ�ƻ������ڴ�����ִ�й���������������󣬶��ᵯ�������û�
	// ��������ֵ����壬 n�� ����n���͵����Զ���ʧ�� ������������֣������ó�0���򵯳����ں�Ͳ���ʧ
	public Integer alert = -1;

	// ʱ��ƻ���һ����ԭ���ģ�һ���ǽ����ɵ����ֶζ�Ӧ��
	public String rawSchedule;
	public Map<String, String> schedule;

	// ����
	public String[] cmds;
	public String line;

	// ƥ��ʱ���ֶ�д���Ƿ���ȷ����ӵ�ʱ���ֶ��� 3-4,6,7,10-14/2,3
	private final static Pattern timeFieldPattern = Pattern
			.compile("(^\\*|^\\d+([,\\-]\\d+)*)(/\\d+([,\\-]\\d+)*)?$");

	// ÿ��ʱ���ֶζ�Ӧ�����ֵ
	@SuppressWarnings("serial")
	private final static Map<String, Integer> fieldMaxNumber = new HashMap<String, Integer>() {
		{
			put("minute", 59);
			put("hour", 23);
			put("day", 31);
			put("month", 12);
			put("week", 6);
		}
	};

	// ÿ��ʱ���ֶζ�Ӧ����Сֵ
	@SuppressWarnings("serial")
	private final static Map<String, Integer> fieldMinNumber = new HashMap<String, Integer>() {
		{
			put("minute", 0);
			put("hour", 0);
			put("day", 1);
			put("month", 1);
			put("week", 0);
		}
	};

	// �����´�����ʱ�䣬ֻ�� powerOffTask ����Ҫ����ֶ�
	private long nextRunTime;

	// ͨ�� schedule �õ���ÿ���ֶ����������������ʱ��ֵ
	private Map<String, List<Integer>> allowedFieldNumbers;

	@SuppressWarnings("serial")
	public Task(String line) throws Exception {
		this.line = line;
		
		// ���üƻ���������� A: Alert, P: Poweroff, S: Startup
		if (line.charAt(0) == '[') {
			Integer last = line.indexOf("]");
			if (last == -1) throw new Exception("�ƻ�����ǰ��ı�־λ�������� " + line);
			for (Integer i = 1; i < last ; i++) {
				switch (line.charAt(i)) {
				case 'A':
				case 'a':
					this.alert = 0;
					Integer idx = i+1;
					String timeout;
					char next = line.charAt(i+1);
					while (next >= '0' && next <= '9') {
						i++;
						next = line.charAt(i+1);
					}
					timeout = line.substring(idx, i+1);
					if (timeout.length() > 0) this.alert = Integer.parseInt(timeout);
					break;
				case 'P':
				case 'p':
					this.isPowerOffTask = true;
					break;
				case 'S':
				case 's':
					this.isStartUpTask = true;
					break;
				}
			}
			this.isPowerOffTask = true;
			
			
			line = line.substring(last+1).trim();
		}
		
		final String[] lineParts = line.split("\\s+", 6);
		if (lineParts.length != 6) {
			throw new Exception("��ʱ�����ʽ����ȷ��ȱ���ֶ� " + line);
		}

		for (Integer i = 0; i < 5; i++) {
			if (!Task.timeFieldPattern.matcher(lineParts[i]).matches()) {
				throw new Exception("��ʱ��ʽ������ȷ��ʱ���ֶδ��� " + lineParts[i]
						+ " \tAt Line: " + line);
			}
		}

		this.rawSchedule = lineParts[0] + " " + lineParts[1] + " "
				+ lineParts[2] + " " + lineParts[3] + " " + lineParts[4];

		final String minute = this.rawSchedule.equals("* * * * *") ? "*/1"
				: lineParts[0];
		this.schedule = new HashMap<String, String>() {
			{
				put("minute", minute);
				put("hour", lineParts[1]);
				put("day", lineParts[2]);
				put("month", lineParts[3]);
				put("week", lineParts[4]);
			}
		};

		this.cmds = lineParts[5].split("\\s+&&\\s+");

		// ��ȡÿ��ʱ���ֶ����������ֵ
		this.allowedFieldNumbers = this.getAllowedFieldNumbers();

		
		// ���ͨ����������ֵ�Ƿ��ܹ��õ�һ���´�����ʱ�䣬������ܣ��׳��쳣
		this.checkFieldNumbers();
		
		
	}

	/**
	 * �õ���ǰ�ƻ�������´�����ʱ��
	 * 
	 * @return
	 * @throws Exception
	 */
	public long getNextRunTime() throws Exception {

		if (this.nextRunTime == 0) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(new Date());
			this.nextRunTime = this.figureOutNextRunTimeByAnyTime(cal.getTimeInMillis());
		}

		return this.nextRunTime;
	}

	/**
	 * ��ǰ�ƻ����������ָ��ʱ�����һ������ʱ��
	 * 	������Ĳ�֮ͬ����������Ỻ����㵽�� nextRunTime �ģ� 
	 * 	nextRunTime �����е�ʱ����Զ����¼���
	 * 
	 * @param timeInMillisecond
	 * @return
	 * @throws Exception
	 */
	public long getNextRunTime(long timeInMillisecond) throws Exception {

		return this.figureOutNextRunTimeByAnyTime(timeInMillisecond);
	}

	/**
	 * ʱ�ֲ��䣬ͨ���ı� �ա��¡��꣬���õ�һ�����ϵ��ܣ��Ӷ��õ�һ�����ʵ��´�����ʱ��
	 * 
	 * ǰ�᣺ 
	 * 1����֤���ֶε� �֡�ʱ ���� 2����ǰ�� �ա��� ���������ֵ�еģ���û����ν���Ƿ�����ֻҪ��С����һ������1����
	 * 
	 * �㷨�� 
	 * 1������ �� ���䣬һ�������� �ա��£�����ǰ�� �ꡢ�¡��ա�ʱ���� ��������õ��� �� �Ƿ���ϣ�������õ��˽�� 
	 * 2������� 1���Ľ�������ϣ�ͬʱ �ա��� ��ֵ�Ѿ������������ �� + 1���ա��� ��Ϊ��Сֵ���������� 1 ��
	 * 
	 * 
	 * @param timeMap
	 * @return
	 * @throws Exception
	 *             �Ҳ������ʵ��´�����ʱ�� ����ݼӵ�����ˣ�
	 */
	private long figureOutNextRunTimeByDMY(Map<String, Integer> timeMap)
			throws Exception {

		Calendar cal = Calendar.getInstance();
		String[] keys = { "day", "month" }; // ѭ���õĹؼ���

		// while (timeMap)

		Boolean got; // �Ƿ��ҵ���һ�����ʵ�ֵ��û�м������Ƿ���ϣ�

		Integer currTime; // ��ǰָ���� ��/��
		List<Integer> allowTimes; // ��������� ��/�� ֵ
		Integer allowSize, // ��������� ��/�� ֵ���ܸ���
		index; // ָ���� ��/�� ������� ��/�� �е�����

		// ������������
		long maxYear = cal.getActualMaximum(Calendar.YEAR) - 10;

		while (true) {
			got = false;
			for (String key : keys) {
				currTime = timeMap.get(key);
				allowTimes = allowedFieldNumbers.get(key);
				
				// ���µĻ�������ϴ������� 29��30��31������Ҫ�ж��µ�ǰ���Ƿ�֧��������Щ����
				if (key.equals("month") && timeMap.get("day") > 28) {
					allowTimes = this.reviseAllowMonths(allowTimes, timeMap.get("year"), timeMap.get("day"));
					if (allowTimes.size() == 0) break;
				}

				// ��һ��ʱ���ֶα�ָ���Ĵ�������ֶ�ֻҪ���ֺ�ָ������ȣ��Ϳ��Ա�֤�����ٽ����´�����ʱ��
				if (got) {
					timeMap.put(key, currTime);
					continue;
				}

				index = allowTimes.indexOf(currTime);
				allowSize = allowTimes.size();

				// ָ����ʱ�������ֵ
				if (index + 1 == allowSize) {
					timeMap.put(key, allowTimes.get(0)); // û�бȵ�ǰʱ���ģ�ֻ��ȡ��С��ֵ��
				} else {
					got = true;
					timeMap.put(key, allowTimes.get(index + 1));
				}
			}

			if (!got) {
				// �� + 1�� ����ֵ���ó���С��
				timeMap.put("year", timeMap.get("year") + 1);
				timeMap.put("day", allowedFieldNumbers.get("day").get(0));
				timeMap.put("month", allowedFieldNumbers.get("month").get(0));

				if (maxYear < timeMap.get("year")) {
					throw new Exception("�Ҳ������ʵ��´�����ʱ�䣨����Ѿ��ӵ������ֵ�ˣ���");
				}
			}

			cal.setTimeInMillis(Helper.reverseJustifyCalendar(timeMap));

			// ���ڼƻ�ʱ���У�����ѭ���� ��֮ǰֱ���ֶ����ܣ���������������Ҿ���������
			if (this.canRunAt(cal.getTimeInMillis()))
				break;
		}
		return cal.getTimeInMillis();
	}

	/**
	 * ���ݵ�ǰ����ʱ�������˼ƻ������´����е�ʱ�䣨����ʱ�䲻������ͺ��룬���������ǲ���0������������0��
	 * 
	 * ˼·�� �ƻ����������ܣ�û���꣬��ϵ�Ƚϸ��ӡ������ǿ��Էֿ������ǣ��֡�ʱ�ı仯��Ӱ���ܡ�Ҫ����ʱ�����Զ�̬�߶���ȥ�õ�һ�����ʵ���
	 * 
	 * �㷨�� 1����Ϊ��ǰʱ���ǿ�����ʱ�䣬���Ա����ա��¡��ܲ��䣬ֻ�ı�֡�ʱ�����ܷ�ó�һ���ȵ�ǰʱ����ֵ���ܵĻ���������´�����ʱ��
	 * 2����1��ûȡ������ֱ�ȡ�֡�ʱ����Сֵ�������ֲ��䣬�ı��ա��¡��꣨�����ܣ�����������´�����ʱ�� => ��һ���ľ����㷨����
	 * figureOutNextRunTimeByDMY ������
	 * 
	 * @return
	 * @throws Exception
	 */
	private long figureOutNextRunTimeByRunTime(long timeInMillisecond)
			throws Exception {
		// ��ǰָ����ʱ������
		Map<String, Integer> timeMap = Helper
				.justifyCalendar(timeInMillisecond);


		Boolean got = false; // �Ƿ��ҵ���һ�����ʵ�ֵ
		String[] keys = { "minute", "hour" }; // ѭ���õĹؼ���

		Integer currTime; // ��ǰָ����ʱ/��
		List<Integer> allowTimes; // ���������ʱ/��ֵ
		Integer allowSize, // ���������ʱ/��ֵ���ܸ���
		index; // ָ���� ʱ/�� ������� ʱ/�� �е�����

		for (String key : keys) {

			currTime = timeMap.get(key);
			allowTimes = allowedFieldNumbers.get(key);

			// ��һ��ʱ���ֶα�ָ���Ĵ�������ֶ�ֻҪ���ֺ�ָ������ȣ��Ϳ��Ա�֤�����ٽ����´�����ʱ��
			if (got) {
				timeMap.put(key, currTime);
				continue;
			}

			index = allowTimes.indexOf(currTime);
			allowSize = allowTimes.size();

			// ָ����ʱ�������ֵ
			if (index + 1 == allowSize) {
				timeMap.put(key, allowTimes.get(0)); // û�бȵ�ǰʱ���ģ�ֻ��ȡ��С��ֵ��
			} else {
				got = true;
				timeMap.put(key, allowTimes.get(index + 1));
			}

		}
		
		if (got) {
			return Helper.reverseJustifyCalendar(timeMap);
		} else {
			return this.figureOutNextRunTimeByDMY(timeMap);
		}
	}
	
	/**
	 * ������������޸���������� month ֵ��ȥ����Щ�����ܵ�
	 */
	private List<Integer> reviseAllowMonths (List<Integer> allows, Integer year, Integer day) {
		List<Integer> result = new ArrayList<Integer>(allows);
		
		if (day == 29) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.YEAR, year);
			cal.set(Calendar.MONTH, Calendar.FEBRUARY);
			Integer maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
			if (maxDay < 29) {
				result.remove(2);
			}
		}
		if (day == 30) {
			result.remove(2);
		}
		if (day == 31) {
			result.remove(2);
			result.remove(4);
			result.remove(6);
			result.remove(9);
			result.remove(11);
		}
		
		return result;
	}
	/**
	 * ��������·��޸���������� day ֵ��ȥ����Щ�����ܵ�
	 */
	private List<Integer> reviseAllowDays (List<Integer> allows, Map<String, Integer> timeMap) {
		
		List<Integer> result = new ArrayList<Integer>();
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(Helper.reverseJustifyCalendar(timeMap));
		
		Integer maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

		// ����ֱ��ɾ�� allows �е����ݣ��������õģ�ɾ�����֮��ı�������Ӱ��
		for (Integer i : allows) {
			if (i <= maxDay) {
				result.add(i);
			}
		}
		
		return result;
	}

	/**
	 * ��������ʱ������ȡ�ƻ�������´����е�ʱ��
	 * 
	 * ˼·�� �õ�һ����С�� �¡��ա�ʱ���� ��ϣ�ʹ��������ƥ��ƻ������ �¡��ա�ʱ���֣�
	 * ���ж��������ܷ���������������������򷵻ؽ������������ϣ� ��ʱ�������ó���Сֵ�����Ӹ� figureOutNextRunTimeByDMY
	 * ����
	 * 
	 * ���Դ˴��㷨�ؼ�����θ�������һ��ʱ��õ������С���¡��ա�ʱ�������
	 * 
	 * �㷨��
	 * ���� got = false����ʾ���Ƿ��ҵ���һ����ָ��ʱ����ֵ�����ΪtrueԤʾ��֮��������ֶζ�ȡ��Сֵ��
	 * ���� back = false (��ʾ������ǰ�������ֶ��Ƿ��Ǵ���һ���ֶλ��˹����ģ����˹���ͬʱ��ʾ֮ǰ�����ֵ��Ҫ��1��)
	 * 	1���� month��day��hour��minute ��˳�����
	 *  2����������ǰ�������Ƿ��� day����day�Ļ���Ҫ���ݵ�ǰ����ݺ��·ݣ������������day���ͼƻ������������dayȡ������
	 *  	�����������Ǹ��ռ���ÿ�µ����������仯�����Զ�����Щ�������ó�31��30��ż������ֿռ����� ���� back = true 
	 *  3��
	 *  	��� got = true
	 *  		ȡ��ǰ�������Сֵ
	 *  	��� back = true
	 *  		ȡ��֮ǰ���ֶα����ֵ�������ܷ��һ��
	 *  			����ܼӣ����ֶ����óɼ�һ����ֵ��got = true
	 *  			������ܼ� => ��ǰ�ֶ��Ƿ��޷��ٺ����� ? �˳�ѭ�� : �������� back = true
	 *  	got = false && back = false
	 *  		�����ȡ����ָ��ֵ��ȵ�ֵ���򱣴��ֵ����������
	 *  		�������ֵ��С��ָ��ֵ�� back = true
	 *  		ʣ�µ������ȡһ�����ñ�ָ��ֵ���ֵ������������ got = true
	 *  
	 *  4���˳�������
	 *  	�����õ���һ����С�� month��day��hour��minute��ϣ����Է������ñ�ָ��ʱ���ͬʱ���㵱ǰ�ƻ�����Ķ����ĸ�ֵ��Ҫ��
	 *  	�ٽ��ָ���� year�����������ϵõ��� week �Ƿ���ϼƻ�����Ҫ��
	 *  		���ϵĻ��ͷ������ֵ
	 *  		�����Ͼ�ȡ hour��minute����Сֵ��ͬʱ����֮ǰд����һ���㷨������ year month day �� week ֮ǰ�Ĺ�ϵ
	 *  
	 * 
	 * @param timeInMillisecond
	 * @return
	 * @throws Exception
	 */
	private long figureOutNextRunTimeByAnyTime(long timeInMillisecond)
			throws Exception {
		// ������������㷨���������
		if (this.canRunAt(timeInMillisecond)) {
			return this.figureOutNextRunTimeByRunTime(timeInMillisecond);
		}
		
		Map<String, Integer> timeMap = Helper
				.justifyCalendar(timeInMillisecond);
		
		String[] keys = {"month", "day", "hour", "minute"};
		String key;
		Integer keyIdx;
		
		List<Integer> allows; // ���������ʱ��ֵ
		Integer allowSize, allowMin, allowMax, currVal, currIdx;
		Boolean got = false, 
				back = false;
		for (keyIdx = 0; keyIdx < keys.length; keyIdx++) {
			key = keys[keyIdx];
			
			allows = this.allowedFieldNumbers.get(key);
			
			// ��������·��޸� days
			if (key.equals("day") && allows.get(allows.size() - 1) > 28) {
				allows = this.reviseAllowDays(allows, timeMap);
				if (allows.size() == 0) {
					back = true;
					keyIdx = keyIdx - 2; // ��ǰ keyIdx = 1�����Կ϶����Ժ��ˣ���2����ѭ����ʱ����1�����Բ��õ���keyIdx = -1
					continue;
				}
			}
		
			
			allowSize = allows.size();
			allowMin = allows.get(0);
			allowMax = allows.get(allowSize - 1);
			currVal  = timeMap.get(key);
			currIdx  = allows.indexOf(currVal);
;
			if (got == true) {
				timeMap.put(key, allowMin);
			} else if (back == true) {
				if (currVal < allowMax) {
					// ȡ��һ���ȵ�ǰ���ֵ�ŵ� timeMap ��
					for (int i : allows) {
						if (i > currVal) {
							timeMap.put(key, i);
							break;
						}
					}
					got = true;
					back = false;
					continue;
					
				} else {
					//System.out.println(keyIdx);
					// ���˵�����㣬�˳�ѭ��
					if (keyIdx == 0) {
						break;
					// ��������
					} else {
						back = true;
						keyIdx = keyIdx - 2;
						continue;
					}
				}
			} else {
				//�����ȡ����ָ��ֵ��ȵ�ֵ���򱣴��ֵ���������� back = false
				//�������ֵ��С��ָ��ֵ�� back = true
				//ʣ�µ������ȡһ�����ñ�ָ��ֵ���ֵ������������ got = true
				if (currIdx != -1) {
					timeMap.put(key, currVal);
					back = false;
					continue;
				} else if (allowMax < currVal) {
					back = true;
					keyIdx = keyIdx - 2;
					continue;
				} else {
					for (int j : allows) {
						if (j > currVal) {
							timeMap.put(key, j);
							break;
						}
					}
					got = true;
				}
			}
		}

		// �����õ���һ����С�� month��day��hour��minute��ϣ����Է������ñ�ָ��ʱ���ͬʱ���㵱ǰ�ƻ�����Ķ����ĸ�ֵ��Ҫ��
		// �ٽ��ָ���� year�����������ϵõ��� week �Ƿ���ϼƻ�����Ҫ��
		// ���ϵĻ��ͷ������ֵ
		// �����Ͼ�ȡ hour��minute����Сֵ��ͬʱ����֮ǰд����һ���㷨������ year month day �� week ֮ǰ�Ĺ�ϵ
		if (got == true) {
			long milliSeconds = Helper.reverseJustifyCalendar(timeMap);
			if (this.canRunAt(milliSeconds)) {
				return milliSeconds;
			}
		}
		timeMap.put("minute", this.allowedFieldNumbers.get("minute").get(0));
		timeMap.put("hour", this.allowedFieldNumbers.get("hour").get(0));
		//System.out.println(timeMap);
		return this.figureOutNextRunTimeByDMY(timeMap);
	}

	/**
	 * �ƻ�������ָ����ʱ���Ƿ�������
	 * 
	 * @param timeInMillisecond
	 * @return
	 */
	public Boolean canRunAt(long timeInMillisecond) {

		List<Integer> numbers;
		Map<String, Integer> time;

		time = Helper.justifyCalendar(timeInMillisecond);

		for (String key : this.allowedFieldNumbers.keySet()) {
			numbers = this.allowedFieldNumbers.get(key);
			// System.out.println(field + ": " + numbers.toString());
			if (!numbers.contains(time.get(key)))
				return false;
		}
		return true;
	}

	/**
	 * �������� ��Ҫ��֤�ṩ��ʱ���ǿ������е�ʱ�䣬����ᱨ��
	 * 
	 * @throws Exception
	 * @return ��������ִ��ʱ���������
	 */
	public String run(long timeInMillisecond) throws Exception {
		if (!this.canRunAt(timeInMillisecond)) {
			throw new Exception("����ļƻ�ʱ�䲻���ϵ�ǰ�ṩ��ʱ�䣬�����޷�ִ��");
		}
		
		StringBuilder sb = new StringBuilder();
		for (String c : this.cmds) {
			sb.append(Helper.execCMD(c));
		}

		// ��ȡ�´�����ʱ��
		this.nextRunTime = this.figureOutNextRunTimeByRunTime(timeInMillisecond);

		return sb.toString();
	}



	/**
	 * �ڲ����������� schedule ����ȡÿʱ���ֶ����������ֵ
	 * 
	 * @return
	 */
	private Map<String, List<Integer>> getAllowedFieldNumbers() {

		// ����ֵ
		Map<String, List<Integer>> result = new HashMap<String, List<Integer>>();

		// TASK_FILE ��ǰ5���ֶα�ʾ�ĺ���
		String[] fields = { "minute", "hour", "day", "month", "week" };

		// * ��ʾ�ֶ�ֻ������Сֵ
		// crontab ������һ�����򣺵�һ���� * ���ֶΣ���֮ǰ������ * �ֶ�ֻ�ܱ�ʾΪ ��Сֵ �� ��֮������� * �ֶο��Ա�ʾΪ����ֵ
		Boolean starRepresentMin = true;

		// ������¼���� field ���������������ֵ
		List<Integer> numbers;

		// ���� field �� "/" �ָ�������������������ֵ
		Set<Integer> preHalfNumbers, postHalfNumbers;

		// TASK_FILE �е����ֶζ�Ӧ��ֵ
		String fieldRawValue;

		Integer rangeStart, rangeEnd, rangeGo,
		// �����ֶ��������Сֵ�����ֵ���Ǹ�����
		min, max;

		// ����ÿ���ֶΣ��ֱ��������ܰ���������ֵ
		for (final String field : fields) {

			fieldRawValue = this.schedule.get(field);
			max = Task.fieldMaxNumber.get(field);
			min = Task.fieldMinNumber.get(field);

			// �ٶ� fieldRawValue ���н���
			// ����������ǰ�� fieldRawValue �� *
			if (fieldRawValue.equals("*")) {
				numbers = new ArrayList<Integer>();
				if (starRepresentMin) {
					numbers.add(min);
				} else {
					for (rangeGo = min; rangeGo <= max; rangeGo++) {
						numbers.add(rangeGo);
					}
				}
				// ������������Խ� fieldRawValue �������(Ҳ�п���ֻ��һ��)��һ���� / ֮ǰ�ģ�һ���� /
				// ֮��ģ�ÿ��Ľ���������һ�µģ������԰�����Щֵ�� 4,6-8,10-2
			} else {
				starRepresentMin = false;

				preHalfNumbers = new HashSet<Integer>();
				postHalfNumbers = new HashSet<Integer>();

				Set<Integer> refHalfNumbers;

				Boolean isFirstHalf = true;
				for (final String half : fieldRawValue.split("/", 2)) {
					// ��ȡ��Ӧ���ݼ�������
					refHalfNumbers = isFirstHalf ? preHalfNumbers
							: postHalfNumbers;

					for (final String perHalf : half.split(",")) {
						if (perHalf.indexOf("-") >= 0) {
							String[] parts = perHalf.split("-");
							rangeStart = new Integer(parts[0]);
							rangeEnd = new Integer(parts[1]);

							if (rangeStart <= rangeEnd) {
								rangeStart = Math.max(rangeStart, min);
								rangeEnd = Math.min(rangeEnd, max);
								for (rangeGo = rangeStart; rangeGo <= rangeEnd; rangeGo++) {
									refHalfNumbers.add(rangeGo);
								}
							} else {
								for (rangeGo = rangeStart; rangeGo <= max; rangeGo++) {
									refHalfNumbers.add(rangeGo);
								}
								for (rangeGo = rangeEnd; rangeGo >= min; rangeGo--) {
									refHalfNumbers.add(rangeGo);
								}
							}
						} else {
							if (perHalf.equals("*")) {
								for (rangeGo = min; rangeGo <= max; rangeGo++) {
									refHalfNumbers.add(rangeGo);
								}
							} else {
								rangeGo = new Integer(perHalf);
								if (rangeGo >= min && rangeGo <= max)
									refHalfNumbers.add(rangeGo);
							}
						}
					}
					isFirstHalf = false;
				}

				// ������������ postHalfNumbers �������ÿ ������Ϊ�գ��� preHalfNumbers ����������е���
				if (postHalfNumbers.isEmpty()) {
					numbers = new ArrayList<Integer>(preHalfNumbers);
				} else {
					numbers = new ArrayList<Integer>();

					for (Integer num : preHalfNumbers) {
						if (numbers.contains(num))
							continue;
						for (Integer per : postHalfNumbers) {
							if (num % per == 0) {
								numbers.add(num);
								break;
							}
						}
					}
				}
			}

			// ��֤�����ݰ���С�������򣬲���������ָ���ķ�Χ��
			Collections.sort(numbers);
			result.put(field, numbers);
		}

		return result;
	}

	
	/**
	 * �������������ֵ���ж����ܷ�õ�һ����Ч���´�����ʱ��
	 * @throws Exception 
	 * 
	 */
	private void checkFieldNumbers () throws Exception {
		// ������ڴ�ʱ��������Ƿ���Եõ�һ���´�����ʱ�䣬���ܵĻ��׳��쳣
		for (String field : this.allowedFieldNumbers.keySet()) {
			if (this.allowedFieldNumbers.get(field).size() == 0) {
				throw new Exception("��ʱ�ƻ����������У����޸� " + this.rawSchedule);
			}
		}

		// ÿ���·ݵ����������ͬ��������Ҫ�����⴦��
		// 1��day����Сֵ��31�Ļ���month������2��4��6��9��11��Щ�µ����
		// 2��day����Сֵ��30�Ļ���month����ֻ��2��
		Integer minDay = this.allowedFieldNumbers.get("day").get(
				this.allowedFieldNumbers.get("day").size() - 1);
		List<Integer> months = this.allowedFieldNumbers.get("month");
		
		if (minDay == 31) {
			Boolean allow = false;
			@SuppressWarnings("serial")
			List<Integer> allowMonths = new ArrayList<Integer>(){{
				add(1);add(3);add(5);add(7);add(8);add(10);add(12);
			}};
			for (Integer month : months) {
				if (allowMonths.indexOf(month) != -1) {
					allow = true;
					break;
				}
			}
			if (!allow) throw new Exception("��ʱ�ƻ�����������޷����� " + this.rawSchedule);
		} else if (minDay == 30) {
			if (months.size() == 1 && months.get(0) == 2) {
				throw new Exception("��ʱ�ƻ�����������޷����� " + this.rawSchedule);
			}
		}
	}
	
	@Override
	public String toString() {
		return "Task: " + this.line;

	}


}
