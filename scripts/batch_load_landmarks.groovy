/*
 * Batch-load landmarks for a whole project.
 *
 * Run via Automate -> Run for project. For each image it loads
 * <project>/landmarks/<image-file-name>.geojson (if present) into the image and adds the objects.
 *
 * IMPORTANT: tick "Save changes to each image" (or equivalent) in the Run-for-project dialog so the
 * loaded landmarks are persisted to the project. Loading assumes the file matches the image geometry.
 */

import qupath.ext.anchor.io.LandmarkIO

def imageData = getCurrentImageData()
def file = new File(buildFilePath(PROJECT_BASE_DIR, "landmarks"), LandmarkIO.baseFileName(imageData) + ".geojson")
if (!file.exists()) {
    println "No landmark file for " + LandmarkIO.baseFileName(imageData) + " - skipping."
    return
}
def loaded = LandmarkIO.load(imageData, file)
println "Loaded " + loaded.size() + " object(s) into " + LandmarkIO.baseFileName(imageData)
