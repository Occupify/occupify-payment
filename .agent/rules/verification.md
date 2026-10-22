# Information Verification and Path Integrity Rule

## Core Principle
Never make assumptions or answer based on speculation. Always verify all information, file locations, directory hierarchies, and configurations using workspace tools before answering the user or making modifications.

## Mandatory Verification Protocols

1. **Verify File Existence and Exact Location Before Referencing**:
   - Always inspect the filesystem using `find`, `list_dir`, or `grep_search` to verify where a file actually resides before referencing, linking, or importing it.
   - Never assume a file is located at the project root or in a specific subfolder without confirming first.

2. **Accurate Relative Path Resolution**:
   - When constructing relative paths (e.g. in Markdown links, configs, imports), calculate the exact directory depth between the source file and the target file (e.g. a file in `.github/` referencing root requires `../`).
   - Never use machine-local paths (e.g. `file:///home/...`) in repository documentation, templates, or shared files. Always use dynamic repository-relative paths.

3. **Pre-Response Fact Checking**:
   - Check and verify all technical claims, configurations, and commands against the current workspace state before presenting answers or proposing edits to the user.
   - Strictly follow a zero-guess policy: if uncertain about any path, setting, or file structure, inspect the relevant files first.
