Skerry 便携模式数据目录 / Portable Mode Data Directory / Каталог данных портативного режима
==============================================================================================

  保留此目录 / Keep this directory / Сохраните этот каталог
    所有应用数据（保险库、密钥、配置）保存在此处。
    适合 U 盘随身携带，或解压即用。
    All application data (vault, keys, configuration) is stored here.
    Ideal for USB drives or portable use.
    Все данные приложения (хранилище, ключи, конфигурация) хранятся здесь.
    Идеально для USB-накопителей или портативного использования.

  删除此目录 / Delete this directory / Удалите этот каталог
    应用自动切换回标准模式，数据保存在系统用户目录下。
    The app falls back to standard mode and stores data in your system
    user directory:  %APPDATA%\Skerry   or   ~/.config/skerry
    Приложение переключается в стандартный режим и сохраняет данные
    в системном каталоге пользователя:  %APPDATA%\Skerry  или  ~/.config/skerry

  提示 / Tip / Совет
    首次启动时目录可以为空，数据会自动创建。
    The directory can be empty on first launch — data is created automatically.
    При первом запуске каталог может быть пустым — данные создаются автоматически.

  Windows 用户注意 / Windows note / Примечание для Windows
    解压路径不能包含空格（如 C:\Tools\Skerry，不要用 C:\Program Files\Skerry）。
    libsodium 加载器在含空格的路径下初始化失败，应用无法启动。
    Pick a folder whose full path contains NO spaces (e.g. C:\Tools\Skerry —
    not C:\Program Files\Skerry). The bundled libsodium loader fails to
    initialize from a path with spaces, and the app will not start.
    Выберите папку, полный путь к которой НЕ содержит пробелов
    (например, C:\Tools\Skerry — не C:\Program Files\Skerry). Встроенный
    загрузчик libsodium не запускается из пути с пробелами, и приложение
    не сможет запуститься.

  Linux 绿色包注意 / Linux portable note / Примечание для Linux
    zip 在 Linux 上直接解压权限完好；若在 Windows 上解压再拷贝过来，
    可执行权限会丢失，请在本目录的上一级执行一次：
      chmod +x skerry/skerry skerry/bin/skerry
    Extracting on Linux keeps the executable bit. If the zip was extracted
    on Windows and copied over, restore it from the folder above:
      chmod +x skerry/skerry skerry/bin/skerry
    При распаковке в Linux права на исполнение сохраняются. Если архив
    был распакован в Windows и скопирован, восстановите их из папки выше:
      chmod +x skerry/skerry skerry/bin/skerry
