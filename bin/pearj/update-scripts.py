#!/usr/bin/env python3

import requests
import base64
import argparse
import os
import re
import json
from collections import namedtuple
import urllib3

ScriptNode = namedtuple('ScriptNode', ['id', 'groovy_path', 'json_path', 'json'])

# We don't want the TLS warnings, as we likely don't have proper TLS certificates to begin with
urllib3.disable_warnings()

class ScriptReload:
    def __init__(self, base_dir: str, base_am_url: str, username: str, password: str) -> None:
        self.base_dir = base_dir
        self.base_am_url = base_am_url
        self.session = requests.Session()

        self.authenticate_am(username=username, password=password)

        pass

    uidRegex = r"^ou=([\w-]+),"

    def find_json_script_node_file(self, groovy_filename) -> ScriptNode:
        print(f"looking for script node for: {groovy_filename}")
        for root, dirs, fnames in os.walk(self.base_dir):
            for fname in fnames:
                if fname.endswith(".json"):
                    # print(f"Searching {fname}")
                    path = os.path.join(root, fname)
                    with open(path) as jsonfp:
                        jsonfile = json.load(fp=jsonfp)
                    
                    try:
                        if isinstance(jsonfile["data"]["script"], str):
                            print(f"Warning: Script Name [{jsonfile['data']['name']}] with file: [{fname}] has a string based script, this is most likely not desired and should be corrected/discarded")
                        else:
                            inline_file = jsonfile["data"]["script"]["$base64:encode"]["$inline"]
                            if inline_file == groovy_filename:
                                print(f"Found a match in {fname}")
                                matches = re.search(self.uidRegex, jsonfile["metadata"]["uid"])

                                return ScriptNode(id=matches.group(1), groovy_path=os.path.join(self.base_dir, groovy_filename), json_path=path, json=jsonfile)
                        
                    except KeyError:
                        pass
        print(f"Warning: Didn't find script node for {groovy_filename}")

    def prepare_payload(self, script_node:ScriptNode):
        payload = script_node.json["data"]
        payload["_id"] = script_node.id

        with open(script_node.groovy_path, 'rb') as groovy_file:
            file_bytes = bytearray(groovy_file.read())
            payload["script"] = base64.b64encode(file_bytes).decode("ascii")
        
        return payload

    def authenticate_am(self, username: str, password: str):
        headers = {
            "X-OpenAM-Username": username,
            "X-OpenAM-Password": password,
            "Accept-API-Version": "protocol=2.0,resource=1.0"
        }

        self.session.post(f"{self.base_am_url}/json/realms/root/authenticate", headers=headers, verify=False)

    def update_script(self, data, script_node: ScriptNode):
        headers = {
            "Accept-API-Version": "protocol=2.0,resource=1.0",
            "Content-type": "application/json"
        }

        res = self.session.put(f"{self.base_am_url}/json/scripts/{script_node.id}", headers=headers, verify=False, data=json.dumps(data))

        print(res)

        print(f"Updated Script {script_node.groovy_path}")

def main():
    parser = argparse.ArgumentParser(description='Reload changed AM scripts')
    parser.add_argument('-b', '--base-dir', required=True)
    parser.add_argument('-u', '--username', required=True)
    parser.add_argument('-p', '--password', required=True)
    parser.add_argument('-a', '--base-am-url', required=True)
    parser.add_argument('changed_files', nargs='*')

    args = parser.parse_args()

    script_reload = ScriptReload(base_dir=args.base_dir, base_am_url=args.base_am_url, username=args.username, password=args.password)
    for file in args.changed_files:
        script_node = script_reload.find_json_script_node_file(groovy_filename=file)

        if script_node:
            data = script_reload.prepare_payload(script_node=script_node)
            script_reload.update_script(data=data, script_node=script_node)

main()