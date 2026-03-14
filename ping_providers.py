import urllib.request
import urllib.error
import ssl
import json

providers = {
    "AllNovelProvider": "https://allnovel.org",
    "AnnasArchive": "https://annas-archive.org",
    "FreewebnovelProvider": "https://freewebnovel.com",
    "FanMtlnProvider": "https://fanmtl.com",
    "GraycityProvider": "https://graycity.net",
    "HiraethTranslationProvider": "https://hiraethtranslation.com",
    "IndoWebNovelProvider": "https://indowebnovel.id",
    "KolNovelProvider": "https://kolnovel.com",
    "LibReadProvider": "https://libread.com",
    "LightNovelTranslationsProvider": "https://lightnoveltranslations.com",
    "MeioNovelProvider": "https://meionovel.id",
    "MoreNovelProvider": "https://morenovel.net",
    "MtlNovelProvider": "https://www.mtlnovel.com",
    "NovelBinProvider": "https://novelbin.com",
    "NovelFullNETProvider": "https://novelfull.net",
    "NovelFullProvider": "https://novelfull.com",
    "NovelFireProvider": "https://novelfire.net",
    "NovelsOnlineProvider": "https://novelsonline.net",
    "NovLoveProvider": "https://novlove.com",
    "PawReadProver": "https://pawread.com",
    "ReadfromnetProvider": "https://readfrom.net",
    "ReadNovelFullProvider": "https://readnovelfull.com",
    "RoyalRoadProvider": "https://www.royalroad.com",
    "SakuraNovelProvider": "https://sakuranovel.id",
    "ScribblehubProvider": "https://www.scribblehub.com",
    "WtrLabProvider": "https://wtr-lab.com",
    "WuxiaBoxProvider": "https://www.wuxiabox.com"
}

user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

working = []
broken = []

print("Starting provider ping test...")
for name, url in providers.items():
    try:
        req = urllib.request.Request(url, headers={'User-Agent': user_agent})
        response = urllib.request.urlopen(req, context=ctx, timeout=10)
        status = response.getcode()
        if status == 200:
            print(f"[OK] {name} ({url}) is UP")
            working.append(name)
        else:
            print(f"[FAIL] {name} ({url}) returned status {status}")
            broken.append(name)
    except urllib.error.HTTPError as e:
        if e.code in [403, 503]:
            # Cloudflare or forbidden, might still be working in app but blocked by simple script
            print(f"[WARN] {name} ({url}) blocked (HTTP {e.code}), assuming UP for now")
            working.append(name)
        elif e.code == 404:
             print(f"[FAIL] {name} ({url}) returned HTTP {e.code}")
             broken.append(name)
        else:
            print(f"[FAIL] {name} ({url}) failed: {e}")
            broken.append(name)
    except Exception as e:
        print(f"[FAIL] {name} ({url}) failed: {e}")
        broken.append(name)

print("\n--- RESULTS ---")
print(f"Working ({len(working)}): {', '.join(working)}")
print(f"Broken ({len(broken)}): {', '.join(broken)}")
