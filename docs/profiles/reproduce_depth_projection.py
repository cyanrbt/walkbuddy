"""Reproduce projection parity with the locked ARCore classes.jar and a JDK, without an AR session."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--arcore-jar', required=True, type=Path, help='classes.jar from the locked com.google.ar:core:1.56.0 AAR')
parser.add_argument('--output', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
jar = args.arcore_jar.resolve()
java_home = os.environ.get('JAVA_HOME')
java = str(Path(java_home)/'bin/java') if java_home else shutil.which('java')
javac = str(Path(java_home)/'bin/javac') if java_home else shutil.which('javac')
javap = str(Path(java_home)/'bin/javap') if java_home else shutil.which('javap')
if not jar.is_file() or not all([java, javac, javap]):
    parser.error('An existing ARCore classes.jar and JDK are required')
files = [root/'app/src/main/java/com/cyanrbt/walkbuddy'/name for name in
         ['DepthProjection.java', 'DepthThresholds.java', 'DepthObstacles.java', 'DepthDrops.java', 'Detection.java', 'ObstacleMask.java']]
files += [root/'app/src/checks/java/com/cyanrbt/walkbuddy/DepthProjectionCases.java', Path(__file__).with_name('DepthProjectionAudit.java')]
baseline = subprocess.check_output(['git', 'show', 'v0.4.31:app/src/main/java/com/cyanrbt/walkbuddy/ArDepthCamera.java'], cwd=root)
assert b'pose.transformPoint(new float[]{' in baseline
integration = root/'app/src/main/java/com/cyanrbt/walkbuddy/ArDepthCamera.java'
current = integration.read_text()
assert 'pose.transformPoint(new float[]{' not in current
assert 'projection.project(pose,i,z,fx,fz,floorY,grid)' in current
assert 'projection.reset();' in current and 'if(!projection.mapped(i))continue;' in current
assert 'intrinsics.getFocalLength(projection.focal,0)' in current and 'intrinsics.getPrincipalPoint(projection.center,0)' in current
with tempfile.TemporaryDirectory(prefix='walkbuddy-depth-projection-') as out:
    subprocess.run([javac, '-encoding', 'UTF-8', '-cp', str(jar), '-d', out, *map(str, files)], check=True)
    report = subprocess.check_output([java, '-cp', out+os.pathsep+str(jar), 'com.cyanrbt.walkbuddy.DepthProjectionAudit'], text=True)
    assert 'DEPTH_PROJECTION_PARITY_PASS cases=96 grid_points=589824' in report
    assert 'DEPTH_PROJECTION_STATE_PASS cases=5' in report
    bytecode = subprocess.check_output([javap, '-classpath', out, '-c', 'com.cyanrbt.walkbuddy.DepthProjection'], text=True)
    project = bytecode[bytecode.index('void project('):]
    assert 'newarray' not in project and 'anewarray' not in project
    assert 'Pose.transformPoint:([FI[FI)V' in project
result = {
    'scope': 'Constructed JVM observations and source/bytecode array-construction bounds; no AR session, ART allocation measurement, FPS, power or device accuracy claim',
    'arcore_version': '1.56.0', 'arcore_classes_jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
    'baseline_tag': 'v0.4.31', 'baseline_ar_depth_camera_sha256': hashlib.sha256(baseline).hexdigest(),
    'source_sha256': {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files+[integration, Path(__file__)]},
    'report': report,
    'grid_width': 64, 'grid_height': 96,
    'visible_array_constructors_per_sampling_pass': {'baseline_formula': '5 + 2 * valid_points', 'baseline_all_6144_valid': 12293,
                                                     'new_projection_workspace_per_pass': 0, 'new_workspace_once': 7},
    'retained_workspace_float_payload_bytes': 147496,
    'new_project_method_has_no_array_creation_bytecode': True,
    'limits': 'Bounds cover the replaced coordinate/intrinsics/point arrays only; unchanged grid/mask/traversal and ARCore internal allocations remain. JVM/ART may eliminate allocations; object headers are excluded.'
}
output = json.dumps(result, ensure_ascii=False, indent=2)+'\n'
if args.output:
    args.output.write_text(output)
else:
    print(output, end='')
