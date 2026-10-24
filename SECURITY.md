# Security

AssetDropKit decides which downloaded files an app trusts and reads. A vulnerability here is a vulnerability in every app that uses it, so please report privately.

- Email founder@vaazhstudios.com with steps to reproduce. Expect an acknowledgement within three days.
- Please give us 90 days before public disclosure; we credit reporters in the changelog unless asked not to.
- In scope: anything that makes an app read a file that does not match its pack manifest, read outside a pack, or fall back to local files in a release build. Out of scope: the stores themselves, and apps that misuse the API.

Supported: the latest minor version. Fixes ship as a patch release and a note in `CHANGELOG.md`.
