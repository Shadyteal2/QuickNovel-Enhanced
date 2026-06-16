import os
import re
import urllib.request
import urllib.error

PROVIDERS_DIR = os.path.join("app", "src", "main", "java", "com", "lagradost", "quicknovel", "providers")

def check_provider(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Extract class name, mainUrl, and name
    name_match = re.search(r'override\s+val\s+name\s*=\s*"([^"]+)"', content)
    url_match = re.search(r'override\s+val\s+mainUrl\s*=\s*"([^"]+)"', content)
    
    if not url_match or not name_match:
        return None

    p_name = name_match.group(1)
    p_url = url_match.group(1)

    print(f"Checking {p_name} ({p_url})... ", end="", flush=True)

    # Perform request with standard User-Agent to bypass simple blocks
    req = urllib.request.Request(
        p_url,
        headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
    )

    try:
        with urllib.request.urlopen(req, timeout=8) as response:
            status = response.getcode()
            if status in [200, 301, 302]:
                print("\033[92m[OK]\033[0m")
                return p_name, p_url, "OK", status
            else:
                print(f"\033[93m[UNEXPECTED STATUS: {status}]\033[0m")
                return p_name, p_url, f"Unexpected Status: {status}", status
    except urllib.error.HTTPError as e:
        # Some sites block basic scraping or return 403/503 for Cloudflare challenges
        if e.code in [403, 503]:
            print("\033[93m[CLOUDFLARE/CHALLENGE DETECTED]\033[0m")
            return p_name, p_url, "Cloudflare/Protection Enabled (May need solver)", e.code
        else:
            print(f"\033[91m[HTTP ERROR {e.code}]\033[0m")
            return p_name, p_url, f"HTTP Error {e.code}", e.code
    except urllib.error.URLError as e:
        print(f"\033[91m[UNREACHABLE: {e.reason}]\033[0m")
        return p_name, p_url, f"Unreachable: {e.reason}", "URLError"
    except Exception as e:
        print(f"\033[91m[ERROR: {str(e)}]\033[0m")
        return p_name, p_url, f"Error: {str(e)}", "Error"

def main():
    if not os.path.exists(PROVIDERS_DIR):
        print(f"Error: Providers directory '{PROVIDERS_DIR}' not found.")
        return

    print("==================================================")
    print("      QuickNovel Provider Health Checker          ")
    print("==================================================")

    results = []
    for filename in os.listdir(PROVIDERS_DIR):
        if filename.endswith(".kt"):
            file_path = os.path.join(PROVIDERS_DIR, filename)
            res = check_provider(file_path)
            if res:
                results.append(res)

    print("\n=================== SUMMARY ======================")
    print(f"{'Provider Name':<18} | {'URL':<30} | {'Status':<15}")
    print("-" * 70)
    for name, url, status, _ in results:
        print(f"{name:<18} | {url:<30} | {status:<15}")
    print("==================================================")

if __name__ == "__main__":
    main()
