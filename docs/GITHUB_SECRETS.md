# GitHub Secrets for Release Signing

Release APK signing is performed only in GitHub Actions. Do not commit `.jks`,
`.keystore`, `keystore.properties`, passwords, aliases, or generated keystore
files to the repository.

## Required Secrets

Add these secrets in the private GitHub repository:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The workflow should decode `KEYSTORE_BASE64` into a temporary file and export
its path as `KEYSTORE_FILE` before running the release build.

## Convert a JKS to Base64

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\release.jks")) | Set-Content -NoNewline "keystore-base64.txt"
```

macOS or Linux:

```bash
base64 -w 0 /path/to/release.jks > keystore-base64.txt
```

If your `base64` command does not support `-w`, use:

```bash
base64 /path/to/release.jks | tr -d '\n' > keystore-base64.txt
```

Copy only the contents of `keystore-base64.txt` into the GitHub secret
`KEYSTORE_BASE64`, then delete the local text file when finished.

## Environment Variables Used by Gradle

`app/build.gradle.kts` reads these environment variables during release builds:

- `KEYSTORE_FILE`: path to the decoded keystore file inside the workflow runner
- `KEYSTORE_PASSWORD`: keystore password
- `KEY_ALIAS`: key alias
- `KEY_PASSWORD`: key password

If any variable is missing, the release signing task fails with a clear error.
Debug builds do not require these variables.
