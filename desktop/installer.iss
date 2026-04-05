; VARYNX Desktop — Inno Setup Installer
; Includes: Desktop Hub, Guardian Service, Auto-start, Clean Uninstall

#define MyAppName "VARYNX Desktop"
#define MyAppVersion "2.0.0-beta"
#define MyAppPublisher "VARYNX"
#define MyAppExeName "VARYNX Desktop.exe"
#define MyServiceName "VarynxGuardianService"
#define MyServiceExe "varynx-service.jar"

[Setup]
AppId={{133700EB-A443-482C-ABB8-E109952746B2}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=build\installer
OutputBaseFilename=VARYNX-Desktop-{#MyAppVersion}-Setup
Compression=lzma2/fast
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
UninstallDisplayName={#MyAppName}
SetupIconFile=src\main\resources\icons\varynx-desktop.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
; Force-close the app before install/update — prevents locked-file failures
CloseApplications=force
CloseApplicationsFilter={#MyAppExeName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "autostart"; Description: "Start VARYNX Guardian Service at login"; GroupDescription: "Service Options:"; Flags: checkedonce

[Files]
; Desktop Hub (JavaFX WebView shell)
Source: "build\jpackage\VARYNX Desktop\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
; Application icon
Source: "src\main\resources\icons\varynx-desktop.ico"; DestDir: "{app}"; Flags: ignoreversion
; Guardian Service JAR
Source: "..\service\build\libs\varynx-service-all.jar"; DestDir: "{app}\service"; DestName: "{#MyServiceExe}"; Flags: ignoreversion
; Service launcher script
Source: "scripts\varynx-service.bat"; DestDir: "{app}\service"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\varynx-desktop.ico"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\varynx-desktop.ico"; Tasks: desktopicon
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"

[InstallDelete]
; Remove stale files from previous version before copying new ones
Type: filesandordirs; Name: "{app}\runtime"
Type: filesandordirs; Name: "{app}\app"
Type: files; Name: "{app}\*.dll"

[Registry]
; Auto-start service at login
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "VarynxGuardianService"; ValueData: """{app}\service\varynx-service.bat"""; Flags: uninsdeletevalue; Tasks: autostart

[Run]
; Start service immediately after install
Filename: "{app}\service\varynx-service.bat"; Parameters: ""; Description: "Start Guardian Service"; Flags: nowait postinstall runhidden
; Launch Hub
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; Kill the desktop app first (locks the EXE and DLLs)
Filename: "taskkill.exe"; Parameters: "/F /IM ""{#MyAppExeName}"""; Flags: runhidden; RunOnceId: "StopDesktop"
; Kill the guardian service (runs via bundled java.exe from jpackage runtime)
Filename: "taskkill.exe"; Parameters: "/F /IM java.exe /FI ""MODULES eq varynx-service.jar"""; Flags: runhidden; RunOnceId: "StopServiceJar"
; Fallback — kill any java.exe whose command line references our install dir
Filename: "{cmd}"; Parameters: "/C wmic process where ""CommandLine like '%varynx-service%'"" call terminate >nul 2>&1"; Flags: runhidden; RunOnceId: "StopServiceWmic"

[UninstallDelete]
; Clean up data directory
Type: filesandordirs; Name: "{userappdata}\VARYNX"
Type: filesandordirs; Name: "{app}\service\logs"
Type: filesandordirs; Name: "{app}"

[Code]
// Kill all VARYNX processes before install/upgrade so files aren't locked
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Result := '';
  // Kill desktop app
  Exec('taskkill.exe', '/F /IM "{#MyAppExeName}"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  // Kill guardian service java.exe (wmic so we can match the command-line)
  Exec(ExpandConstant('{cmd}'), '/C wmic process where "CommandLine like ''%varynx-service%''" call terminate >nul 2>&1', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  // Brief pause to let OS release file handles
  Sleep(1000);
end;
