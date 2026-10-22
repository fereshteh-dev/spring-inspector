"""Turns failed tests in Surefire reports into GitHub annotations, which can be read without opening the log."""
import glob
import xml.etree.ElementTree as ElementTree

reported = 0
for report in sorted(glob.glob("**/target/surefire-reports/TEST-*.xml", recursive=True)):
    try:
        suite = ElementTree.parse(report).getroot()
    except ElementTree.ParseError:
        continue
    for case in suite.iter("testcase"):
        for problem in list(case.findall("failure")) + list(case.findall("error")):
            message = (problem.get("message") or problem.text or "").strip().replace("\r", "")
            first_lines = " | ".join(line.strip() for line in message.splitlines()[:6] if line.strip())
            title = f"{case.get('classname', '')}.{case.get('name', '')}"
            print(f"::error title={title}::{first_lines[:900]}")
            reported += 1
if reported == 0:
    print("::error title=Build failed::No failed tests found; the failure is in compilation, formatting or a check.")
