@echo off
set base=%~dp0

set class=%base%/bin

set class_path=%class%

@REM java -cp %class_path% com.main.Crontab %~1 %~2
java -cp "E:\Coding_Area\Python\tools\crontab-master\bin" com.main.Crontab %~1 %~2
@pause