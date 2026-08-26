/*
 * Batch-export landmarks for a whole project.
 *
 * Run via Automate -> Run for project (or Run for selected images). For each image it writes the
 * landmark (LM-*) annotations to <project>/landmarks/<image-file-name>.geojson.
 *
 * Requires the Anchor extension on the classpath. Coordinates are full-resolution image pixels.
 */

import qupath.ext.anchor.io.LandmarkIO

def imageData = getCurrentImageData()
def landmarks = LandmarkIO.landmarks(imageData)
if (landmarks.isEmpty()) {
    println "No landmarks in " + LandmarkIO.baseFileName(imageData) + " - skipping."
    return
}

def dir = buildFilePath(PROJECT_BASE_DIR, "landmarks")
mkdirs(dir)
def file = new File(dir, LandmarkIO.baseFileName(imageData) + ".geojson")   // use ".csv" for CSV instead
LandmarkIO.exportGeoJson(imageData, file)                                    // or LandmarkIO.export(imageData, file)
println "Exported " + landmarks.size() + " landmark(s) to " + file
