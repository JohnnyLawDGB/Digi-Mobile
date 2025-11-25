# DigiByte Core submodule setup

The `core/` directory is meant to track the DigiByte Core repository as a git submodule pinned to v8.26.
Use the commands below to add and pin the submodule if it has not already been initialized.

## Add the submodule

```bash
git submodule add https://github.com/digibyte/digibyte.git ./core
```

## Check out the expected release

After the submodule has been added, change into the `core` directory and check out the desired release tag or commit.
For v8.26, run:

```bash
cd core
git fetch --tags
# Replace v8.26 with a specific commit hash if you need to pin to an exact commit

git checkout v8.26
```

If the submodule already exists, refresh it with:

```bash
git submodule update --init --recursive core
```
