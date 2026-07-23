Skerry 便携模式数据目录 / Portable Mode Data Directory
=========================================================

  保留此目录 / Keep this directory
    所有应用数据（保险库、密钥、配置）保存在此处。
    All application data (vault, keys, configuration) is stored here.
    适合 U 盘随身携带，或解压即用 / Ideal for USB drives or portable use.

  删除此目录 / Delete this directory
    应用自动切换回标准模式，数据保存在系统用户目录下。
    The app falls back to standard mode and stores data in your system
    user directory:  %APPDATA%\Skerry   or   ~/.config/skerry

  提示 / Tip： 首次启动时目录可以为空，数据会自动创建。
              The directory can be empty on first launch — data is created automatically.

  Linux 绿色包注意 / Linux portable zip note：
    zip 在 Linux 上直接解压权限完好；若在 Windows 上解压再拷贝过来，
    可执行权限会丢失，请在本目录的上一级执行一次：
      chmod +x skerry/skerry skerry/bin/skerry
    Extracting on Linux keeps the executable bit. If the zip was extracted
    on Windows and copied over, restore it from the folder above:
      chmod +x skerry/skerry skerry/bin/skerry
