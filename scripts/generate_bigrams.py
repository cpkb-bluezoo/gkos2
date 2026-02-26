#!/usr/bin/env python3
"""
Generate bundled bigram data for GKOS keyboard word suggestions.

Downloads Peter Norvig's bigram frequency data (derived from the Google
Web Trillion Word Corpus) and filters it against the app's existing word
lists to produce compact gzip-compressed bigram files.

Usage:
    python3 scripts/generate_bigrams.py [--lang en] [--max-context 3000] [--max-followers 10]

Output:
    app/src/main/assets/bigrams/{lang}.gz

Compact format (gzip-compressed text):
    contextWord<TAB>follower1,follower2,...,followerN
Followers are in frequency order (most frequent first); counts are
omitted since only ordering matters.

Only bigrams where BOTH words appear in wordlists/{lang}.txt are included.

Data source:
    https://norvig.com/ngrams/count_2w.txt  (~5.6 MB, English bigrams)

For other languages, provide a local bigram file in the same format
(space-separated: word1 word2 count) via --input.
"""

import argparse
import gzip
import os
import sys
import urllib.request
from collections import defaultdict

NORVIG_BIGRAM_URL = "https://norvig.com/ngrams/count_2w.txt"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
WORDLIST_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "wordlists")
OUTPUT_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "bigrams")


def load_wordlist(lang):
    """Load the set of known words for a language."""
    path = os.path.join(WORDLIST_DIR, f"{lang}.txt")
    if not os.path.exists(path):
        sys.exit(f"Word list not found: {path}")
    words = set()
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split(" ", 1)
            if parts:
                words.add(parts[0].lower())
    print(f"Loaded {len(words)} words from {lang}.txt")
    return words


def download_norvig_bigrams(cache_path):
    """Download Norvig's bigram file, caching locally."""
    if os.path.exists(cache_path):
        print(f"Using cached bigram data: {cache_path}")
        return
    print(f"Downloading {NORVIG_BIGRAM_URL} ...")
    urllib.request.urlretrieve(NORVIG_BIGRAM_URL, cache_path)
    print(f"Saved to {cache_path}")


def parse_bigrams(path):
    """Parse a bigram file.  Supports Norvig format (word1 word2<TAB>count)
    and generic three-column format (word1<TAB>word2<TAB>count or
    word1 word2 count)."""
    bigrams = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            # Norvig format: "word1 word2\tcount"
            if "\t" in line:
                phrase, _, count_str = line.partition("\t")
                words = phrase.split()
                if len(words) == 2:
                    try:
                        bigrams.append((words[0].lower(), words[1].lower(),
                                        int(count_str.strip())))
                    except ValueError:
                        pass
                    continue
            # Fallback: space-separated "word1 word2 count"
            parts = line.split()
            if len(parts) >= 3:
                try:
                    bigrams.append((parts[0].lower(), parts[1].lower(),
                                    int(parts[2])))
                except ValueError:
                    continue
    print(f"Parsed {len(bigrams)} raw bigram entries")
    return bigrams


def filter_and_group(bigrams, wordset, max_context, max_followers):
    """Filter bigrams to known words, group by context, keep top followers."""
    grouped = defaultdict(list)
    for w1, w2, count in bigrams:
        if w1 in wordset and w2 in wordset and w1 != w2:
            grouped[w1].append((w2, count))

    # Sort each context's followers by count descending, keep top N
    for key in grouped:
        grouped[key].sort(key=lambda x: x[1], reverse=True)
        grouped[key] = grouped[key][:max_followers]

    # Rank context words by total follower count, keep top M
    ranked = sorted(grouped.items(),
                    key=lambda x: sum(c for _, c in x[1]),
                    reverse=True)
    ranked = ranked[:max_context]

    total = sum(len(followers) for _, followers in ranked)
    print(f"Filtered to {len(ranked)} context words, {total} bigram entries")
    return ranked


def write_output(ranked, lang):
    """Write the compressed bigram file."""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    path = os.path.join(OUTPUT_DIR, f"{lang}.gz")
    total_entries = 0
    with gzip.open(path, "wt", encoding="utf-8") as f:
        for context_word, followers in ranked:
            follower_words = [w for w, _ in followers]
            f.write(f"{context_word}\t{','.join(follower_words)}\n")
            total_entries += len(follower_words)
    size = os.path.getsize(path)
    print(f"Wrote {total_entries} entries ({len(ranked)} context words) "
          f"to {path} ({size:,} bytes)")


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lang", default="en",
                        help="Language code (default: en)")
    parser.add_argument("--input", default=None,
                        help="Path to a local bigram file (overrides download)")
    parser.add_argument("--max-context", type=int, default=3000,
                        help="Max context words to keep (default: 3000)")
    parser.add_argument("--max-followers", type=int, default=10,
                        help="Max followers per context word (default: 10)")
    args = parser.parse_args()

    wordset = load_wordlist(args.lang)

    if args.input:
        bigram_path = args.input
    else:
        cache_dir = os.path.join(SCRIPT_DIR, ".cache")
        os.makedirs(cache_dir, exist_ok=True)
        bigram_path = os.path.join(cache_dir, "count_2w.txt")
        download_norvig_bigrams(bigram_path)

    bigrams = parse_bigrams(bigram_path)
    ranked = filter_and_group(bigrams, wordset, args.max_context, args.max_followers)
    write_output(ranked, args.lang)
    print("Done.")


if __name__ == "__main__":
    main()
