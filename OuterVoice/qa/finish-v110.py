from pathlib import Path
source=Path(__file__).with_name('check-v110.py').read_text(encoding='utf-8')
exec(source[:source.index("adb('root')")])
tree=dump('v110-imported'); assert 'welcome.wav' in texts(tree); shot('v110-imported'); click(tree,'Cancel')
print('PASS picker absent -> permission -> built-in browser; invalid rejection and valid WAV import',flush=True)
exec(source[source.index("tree=dump('v110-final-home')"):])
