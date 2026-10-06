from pathlib import Path
source=Path(__file__).with_name('check-v110.py').read_text(encoding='utf-8')
exec(source[:source.index("adb('root')")])
tree=dump('v110-remove-confirm'); click(tree,'Cancel')
recorded=max([x for x in filelist('files') if x.endswith('.wav')],key=lambda x:int(adb('shell','stat','-c','%Y',private('files/'+x)).strip()))
exec(source[source.index('# Recorded test'):])
