@echo off
set "JAVA_HOME=D:\codexFiles\Java\jdk8u492-b09"
set "MAVEN_HOME=D:\codexFiles\Maven\apache-maven-3.9.9"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

start "stock-auth-8081" /D "D:\codexFiles\lianghua\backend\stock-auth" "%JAVA_HOME%\bin\java.exe" "-Dfile.encoding=UTF-8" -jar target\stock-auth-1.0.0.jar
start "stock-stock-8082" /D "D:\codexFiles\lianghua\backend\stock-stock" "%JAVA_HOME%\bin\java.exe" "-Dfile.encoding=UTF-8" -jar target\stock-stock-1.0.0.jar
start "stock-trade-8083" /D "D:\codexFiles\lianghua\backend\stock-trade" "%JAVA_HOME%\bin\java.exe" "-Dfile.encoding=UTF-8" -jar target\stock-trade-1.0.0.jar
start "stock-analysis-8084" /D "D:\codexFiles\lianghua\backend\stock-analysis" "%JAVA_HOME%\bin\java.exe" "-Dfile.encoding=UTF-8" -jar target\stock-analysis-1.0.0.jar
start "stock-ai-8085" /D "D:\codexFiles\lianghua\backend\stock-ai" "%JAVA_HOME%\bin\java.exe" "-Dfile.encoding=UTF-8" -jar target\stock-ai-1.0.0.jar
