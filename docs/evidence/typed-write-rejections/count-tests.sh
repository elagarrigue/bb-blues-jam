cd /c/Users/Emmanuel/AndroidStudioProjects/BBBluesJam
python - <<'PY'
import glob,re
n=0
for f in glob.glob('**/build/test-results/**/*.xml',recursive=True):
    m=re.search(r'<testsuite [^>]*tests="(\d+)"',open(f,encoding='utf-8').read(2000))
    if m:n+=int(m.group(1))
print("testcases",n)
PY
