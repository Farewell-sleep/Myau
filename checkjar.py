# -*- coding: utf-8 -*-
import zipfile, sys
jar = r'C:\Games\OpenMyauPP-OpenMyauPP-1.0.1\build\libs\Myau-1.0.0.jar'
try:
    z = zipfile.ZipFile(jar)
    names = z.namelist()
    sys.stdout.write('total: %d\n' % len(names))
    keys = ['myau/OpenMyau.class','myau/init/Initializer.class','myau/mixin/MixinMinecraft.class',
            'myau/module/modules/HUD.class','myau/util/shader/KawaseBlur.class',
            'myau/risefont/RiseFontManager.class','myau/ui/liquid/LiquidClickGui.class',
            'mixins.myau.json','META-INF/MANIFEST.MF']
    for k in keys:
        hit = [n for n in names if n == k]
        sys.stdout.write('%s -> %s\n' % (k, 'OK' if hit else 'MISSING'))
    sys.stdout.write('--- mixins.myau.json ---\n')
    try:
        sys.stdout.write(z.read('mixins.myau.json').decode('utf-8'))
    except Exception as e:
        sys.stdout.write('read err %r\n' % e)
    sys.stdout.write('--- MANIFEST ---\n')
    try:
        sys.stdout.write(z.read('META-INF/MANIFEST.MF').decode('utf-8'))
    except Exception as e:
        sys.stdout.write('read err %r\n' % e)
    z.close()
except Exception as e:
    sys.stderr.write('FATAL: %r\n' % e)
    sys.exit(1)
