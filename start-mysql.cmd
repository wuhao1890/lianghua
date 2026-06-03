@echo off
start "mysql-lianghua-3306" /D "D:\codexFiles" "D:\mysql-8.0.15-winx64\bin\mysqld.exe" --basedir=D:\mysql-8.0.15-winx64 --datadir=D:\codexFiles\mysql-lianghua-data --port=3306 --character-set-server=utf8 --console
