@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership. The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License. You may obtain a copy of the License at
@REM
@REM https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied. See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.2.0
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PROPS_PATH=%~dp0.mvn\wrapper\maven-wrapper.properties
@SET __MVNW_DIST_URL_STATUS__=

@SETLOCAL
@FOR /F "usebackq tokens=1,* delims==" %%A IN ("%__MVNW_PROPS_PATH%") DO (
  @IF "%%A"=="distributionUrl" SET __MVNW_DIST_URL__=%%B
  @IF "%%A"=="wrapperUrl" SET __MVNW_JAR_URL__=%%B
)

@SET __MVNW_WRAPPER_JAR_PATH=%~dp0.mvn\wrapper\maven-wrapper.jar
@SET MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists

@FOR /F "tokens=2 delims=/" %%A IN ("%__MVNW_DIST_URL__: =%") DO (
  @IF "%%A"=="apache-maven-3.9.6-bin.zip" @SET __MVNW_MAVEN_VERSION__=apache-maven-3.9.6
)

@IF NOT EXIST "%USERPROFILE%\.m2\wrapper\dists\%__MVNW_MAVEN_VERSION__%\bin\mvn.cmd" (
  @ECHO Downloading Maven...
  @powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $url = '%__MVNW_DIST_URL__: =%'; $dest = '%TEMP%\maven.zip'; Invoke-WebRequest -Uri $url -OutFile $dest; Expand-Archive -Path $dest -DestinationPath '%USERPROFILE%\.m2\wrapper\dists' -Force }"
)

@SET __MVNW_MAVEN_CMD__=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6\bin\mvn.cmd
@IF NOT EXIST "%__MVNW_MAVEN_CMD__%" (
  @ECHO ERROR: Maven not found at %__MVNW_MAVEN_CMD__%
  @EXIT /B 1
)

@"%__MVNW_MAVEN_CMD__%" %*
