# Releasing New DevToolsClient Versions

A number of steps are needed to build this repo against a new specific version. In general this will be a specific release of chrome stable. These docs will walk through the example of building against chrome stable `91.0.4472.114`. You will need to `pip install argparse` to run the convert script in this folder.

### Step 1: Fetch pdl Files

These files are the source of the protocol json needed to compile this repo. To start, fetch the browser_protocol.pdl file, you can look at the open source chromium code at a url like:

https://source.chromium.org/chromium/chromium/src/+/refs/tags/91.0.4472.114:third_party/blink/public/devtools_protocol/browser_protocol.pdl

Copy the content of this file to CodeGeneration/src/main/resources/browser_protocol.pdl

Run the pdl_to_json.py scipt to update the corresponding json file:

```
python pdl_to_json.py --pdl_file ../CodeGeneration/src/main/resources/browser_protocol.pdl --json_file ../CodeGeneration/src/main/resources/browser_protocol.json
```

Next, find the version of v8 used by the tagged version of chrome. This can be found at the /json/version endpoint when connecting to the devtools port when running chrome. For `91.0.4472.114` this is V8 `9.1.269.36`. From that, you can find the js_protocol.pdl at a url like:

https://github.com/v8/v8/blob/9.1.269.36/include/js_protocol.pdl

Copy this content to CodeGeneration/src/main/resources/js_protocol.pdl

Run the convert script again for this file:

```
python pdl_to_json.py --pdl_file ../CodeGeneration/src/main/resources/js_protocol.pdl --json_file ../CodeGeneration/src/main/resources/js_protocol.json
```

### Step 2: Building and Tagging

In the root pom of this repo, update the properties section with the new versions for easy reference:

```
<properties>
    <chromium.version>91.0.4472.114</chromium.version>
    <v8.version>9.1.269.36</v8.version>
  </properties>
```

Also update the version of each artificat in it's pom.xml to be {chrome version}-SNAPSHOT. e.g. `91.0.4472.114-SNAPSHOT`. This allows users to more easily lock to a specific chrome version, but still get snapshot updates to any other parts of the client.

Next run a build in the root of this repo with `mvn clean verify` to ensure no changes in the protocol broke the build of the client. Fix any issues that arise.

Push a branch with the same name as the chrome version and tag the resulting commit chrome version as well. Open a PR with the changes.

```
git checkout -b 91.0.4472.114
git add -A
git commit -am "Bump chrome to 91.0.4472.114, V8 to 9.1.269.36"
git tag 91.0.4472.114
git push origin head
```