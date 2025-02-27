package com.muwire.core.mesh

import java.util.stream.Collectors

import com.muwire.core.Constants
import com.muwire.core.InfoHash
import com.muwire.core.MuWireSettings
import com.muwire.core.Persona
import com.muwire.core.download.Pieces
import com.muwire.core.download.SourceDiscoveredEvent
import com.muwire.core.files.FileManager
import com.muwire.core.util.DataUtil

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.i2p.data.Base64

class MeshManager {

    private final Map<InfoHash, Mesh> meshes = Collections.synchronizedMap(new HashMap<>())
    private final FileManager fileManager
    private final File home
    private final MuWireSettings settings

    MeshManager(FileManager fileManager, File home, MuWireSettings settings) {
        this.fileManager = fileManager
        this.home = home
        this.settings = settings
        load()
    }

    Mesh get(InfoHash infoHash) {
        meshes.get(infoHash)
    }

    Mesh getOrCreate(InfoHash infoHash, int nPieces) {
        synchronized(meshes) {
            if (meshes.containsKey(infoHash))
                return meshes.get(infoHash)
            Pieces pieces = new Pieces(nPieces, settings.downloadSequentialRatio)
            if (fileManager.rootToFiles.containsKey(infoHash)) {
                for (int i = 0; i < nPieces; i++)
                    pieces.markDownloaded(i)
            }
            Mesh rv = new Mesh(infoHash, pieces)
            meshes.put(infoHash, rv)
            return rv
        }
    }

    void onSourceDiscoveredEvent(SourceDiscoveredEvent e) {
        Mesh mesh = meshes.get(e.infoHash)
        if (mesh == null)
            return
       mesh.sources.add(e.source)
       save()
    }

    private void save() {
        File meshFile = new File(home, "mesh.json")
        synchronized(meshes) {
            meshFile.withPrintWriter { writer ->
                meshes.values().each { mesh ->
                    def json = [:]
                    json.timestamp = System.currentTimeMillis()
                    json.infoHash = Base64.encode(mesh.infoHash.getRoot())
                    json.sources = mesh.sources.stream().map({it.toBase64()}).collect(Collectors.toList())
                    json.nPieces = mesh.pieces.nPieces
                    json.xHave = DataUtil.encodeXHave(mesh.pieces.downloaded, mesh.pieces.nPieces)
                    writer.println(JsonOutput.toJson(json))
                }
            }
        }
    }

    private void load() {
        File meshFile = new File(home, "mesh.json")
        if (!meshFile.exists())
            return
        long now = System.currentTimeMillis()
        JsonSlurper slurper = new JsonSlurper()
        meshFile.eachLine {
            def json = slurper.parseText(it)
            if (now - json.timestamp > settings.meshExpiration * 60 * 1000)
                return
            InfoHash infoHash = new InfoHash(Base64.decode(json.infoHash))
            Pieces pieces = new Pieces(json.nPieces, settings.downloadSequentialRatio)

            Mesh mesh = new Mesh(infoHash, pieces)
            json.sources.each { source ->
                Persona persona = new Persona(new ByteArrayInputStream(Base64.decode(source)))
                mesh.sources.add(persona)
            }

            if (json.xHave != null)
                DataUtil.decodeXHave(json.xHave).each { pieces.markDownloaded(it) }

            if (!mesh.sources.isEmpty())
                meshes.put(infoHash, mesh)
        }
    }
}
