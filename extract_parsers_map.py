import os
import re

parsers_dir = r"C:\Users\vansh\Documents\Vibecode\WebToEpub\plugin\js\parsers"
output_file = r"app\src\main\java\com\lagradost\quicknovel\providers\WebToEpubMap.kt"

# Regex patterns
register_pattern = re.compile(r'parserFactory\.register(?:DeadSite)?\(\s*["\']([^"\']+)["\']')
register_url_rule_pattern = re.compile(r'parserFactory\.registerUrlRule\(\s*url\s*=>\s*.*includes\(\s*["\']([^"\']+)["\']')

mappings = []

for filename in os.listdir(parsers_dir):
    if not filename.endswith(".js"):
        continue
    filepath = os.path.join(parsers_dir, filename)
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()
        
        # Find explicit registers
        registers = register_pattern.findall(content)
        for r in registers:
            mappings.append((r, filename, "exact"))
            
        # Find url includes rules
        url_rules = register_url_rule_pattern.findall(content)
        for rule in url_rules:
            mappings.append((rule, filename, "contains"))

# Generate Kotlin file content
kotlin_content = """package com.lagradost.quicknovel.providers

object WebToEpubMap {
    // Mapping of domain/substring to parser filename
    val exactMappings = mapOf(
"""

for host, filename, match_type in sorted(mappings):
    if match_type == "exact":
        kotlin_content += f'        "{host}" to "{filename}",\n'

kotlin_content += """    )

    val containsMappings = mapOf(
"""

for host, filename, match_type in sorted(mappings):
    if match_type == "contains":
        kotlin_content += f'        "{host}" to "{filename}",\n'

kotlin_content += """    )

    fun getParserForUrl(url: String): String? {
        val host = try {
            java.net.URL(url).host.lowercase().removePrefix("www.")
        } catch (e: Exception) {
            return null
        }

        // Try exact match first
        exactMappings[host]?.let { return it }

        // Try exact match on subdomains/suffixes
        for ((domain, parser) in exactMappings) {
            if (host == domain || host.endsWith("." + domain)) {
                return parser
            }
        }

        // Try contains rules
        for ((pattern, parser) in containsMappings) {
            if (host.contains(pattern)) {
                return parser
            }
        }

        return null
    }
}
"""

with open(output_file, "w", encoding="utf-8") as f:
    f.write(kotlin_content)

print(f"Successfully generated map with {len(mappings)} entries.")
