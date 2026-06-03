Set shell = CreateObject("WScript.Shell")
shell.CurrentDirectory = "D:\codexFiles\lianghua"
shell.Run "powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File ""D:\codexFiles\lianghua\scripts\ai-lab-sync-iterate-v3.ps1""", 0, True
