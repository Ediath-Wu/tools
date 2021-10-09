@ECHO OFF
schtasks /create /tn "MyCrontabTask" /tr "nircmd.exe exec hide java -cp E:\Coding_Area\Python\tools\crontab-master\bin com.main.Crontab -r" /sc MINUTE /mo 1
pause