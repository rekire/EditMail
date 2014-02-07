@echo off
call ..\..\maven-setup.bat
mvn package source:jar javadoc:jar gpg:sign repository:bundle-create
pause > nul
