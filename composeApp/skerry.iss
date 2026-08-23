; Inno Setup script for SkerrySSH Windows installer
; Build with: iscc skerry.iss
; Requires jpackage app-image at: composeApp/build/compose/binaries/main/app/Skerry/

#define MyAppName "Skerry"
#define MyAppPublisher "onepve"
#define MyAppURL "https://ssh.onepve.com"
#define MyAppExeName "Skerry.exe"

; These are overridden via command line: /DMyAppVersion=0.1.21 /DAppImageDir=...
; Paths are relative to this script's location (composeApp/).
#ifndef MyAppVersion
  #define MyAppVersion "0.1.0"
#endif
#ifndef AppImageDir
  #define AppImageDir "build\compose\binaries\main\app\Skerry"
#endif

[Setup]
AppId={{A3F8B2C1-4D5E-6F7A-8B9C-0D1E2F3A4B5C}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={localappdata}\\{#MyAppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputBaseFilename=Skerry-{#MyAppVersion}-setup
OutputDir=build\compose\binaries\main\exe
SetupIconFile=icons\skerry.ico
Compression=lzma2/ultra64
SolidCompression=yes
Uninstallable=yes
UninstallDisplayName={#MyAppName}
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription=Open-source cross-platform SSH client
WizardStyle=modern
; jpackage apps run as launcher (Skerry.exe) + JVM child (java.exe): Inno's default
; CloseApplications only pings the launcher window with WM_CLOSE, which never terminates
; the JVM holding the file locks -> the "auto close" dialog fails forever. Kill the whole
; process tree silently before installing instead (see PrepareToInstall in [Code]).
CloseApplications=no
RestartApplications=no

; Show a pre-install info page explaining config/data persistence
InfoBeforeFile=install-info.txt

[Languages]
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; Kill running instances before uninstall
Filename: "taskkill"; Parameters: "/f /im {#MyAppExeName}"; Flags: runhidden waituntilterminated

[Code]
// Kill a running Skerry (launcher + JVM child process tree) before files are replaced.
// CloseApplications=no above disables Inno's broken WM_CLOSE-based auto-close for jpackage
// apps; /t kills the whole tree so the java.exe child can't keep file locks.
procedure KillRunningApp;
var
  ResultCode: Integer;
begin
  Exec('taskkill', '/f /t /im {#MyAppExeName}', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  KillRunningApp;
end;

// Detect and silently uninstall previous version before installing
function GetUninstallString: String;
var
  UninstallKey: String;
begin
  Result := '';
  UninstallKey := 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#emit SetupSetting("AppId")}_is1';
  if RegQueryStringValue(HKLM, UninstallKey, 'UninstallString', Result) then
    Exit;
  if RegQueryStringValue(HKCU, UninstallKey, 'UninstallString', Result) then
    Exit;
end;

function InitializeSetup: Boolean;
var
  UninstallStr: String;
  ResultCode: Integer;
begin
  Result := True;
  UninstallStr := GetUninstallString;
  if UninstallStr <> '' then
  begin
    // Run uninstaller silently and wait for it to finish
    if Exec(RemoveQuotes(UninstallStr), '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
    begin
      // Brief pause to let file locks release
      Sleep(1500);
    end;
  end;
end;
