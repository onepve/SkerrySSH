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
DefaultDirName={localappdata}\{#MyAppName}
DisableDirPage=yes
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
