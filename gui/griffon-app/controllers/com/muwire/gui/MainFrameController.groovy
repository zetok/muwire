package com.muwire.gui

import griffon.core.GriffonApplication
import griffon.core.artifact.GriffonController
import griffon.core.controller.ControllerAction
import griffon.core.mvc.MVCGroup
import griffon.core.mvc.MVCGroupConfiguration
import griffon.inject.MVCMember
import griffon.metadata.ArtifactProviderFor
import net.i2p.data.Base64

import javax.annotation.Nonnull
import javax.inject.Inject
import javax.swing.JTable

import com.muwire.core.Constants
import com.muwire.core.Core
import com.muwire.core.Persona
import com.muwire.core.SharedFile
import com.muwire.core.download.DownloadStartedEvent
import com.muwire.core.download.UIDownloadCancelledEvent
import com.muwire.core.download.UIDownloadEvent
import com.muwire.core.download.UIDownloadPausedEvent
import com.muwire.core.download.UIDownloadResumedEvent
import com.muwire.core.files.DirectoryUnsharedEvent
import com.muwire.core.files.FileUnsharedEvent
import com.muwire.core.search.QueryEvent
import com.muwire.core.search.SearchEvent
import com.muwire.core.trust.RemoteTrustList
import com.muwire.core.trust.TrustEvent
import com.muwire.core.trust.TrustLevel
import com.muwire.core.trust.TrustSubscriptionEvent

@ArtifactProviderFor(GriffonController)
class MainFrameController {
    @Inject @Nonnull GriffonApplication application
    @MVCMember @Nonnull
    FactoryBuilderSupport builder

    @MVCMember @Nonnull
    MainFrameModel model
    @MVCMember @Nonnull
    MainFrameView view

    private volatile Core core

    @ControllerAction
    void search() {
        def cardsPanel = builder.getVariable("cards-panel")
        cardsPanel.getLayout().show(cardsPanel, "search window")

        def search = builder.getVariable("search-field").text
        search = search.trim()
        if (search.length() == 0)
            return
        if (search.length() > 128)
            search = search.substring(0,128)
        def uuid = UUID.randomUUID()
        Map<String, Object> params = new HashMap<>()
        params["search-terms"] = search
        params["uuid"] = uuid.toString()
        params["core"] = core
        def group = mvcGroup.createMVCGroup("SearchTab", uuid.toString(), params)
        model.results[uuid.toString()] = group

        boolean hashSearch = false
        byte [] root = null
        if (search.length() == 44 && search.indexOf(" ") < 0) {
            try {
                root = Base64.decode(search)
                hashSearch = true
            } catch (Exception e) {
                // not a hash search
            }
        }

        def searchEvent
        if (hashSearch) {
            searchEvent = new SearchEvent(searchHash : root, uuid : uuid, oobInfohash: true)
        } else {
            // this can be improved a lot
            def replaced = search.toLowerCase().trim().replaceAll(Constants.SPLIT_PATTERN, " ")
            def terms = replaced.split(" ")
            def nonEmpty = []
            terms.each { if (it.length() > 0) nonEmpty << it }
            searchEvent = new SearchEvent(searchTerms : nonEmpty, uuid : uuid, oobInfohash: true)
        }
        core.eventBus.publish(new QueryEvent(searchEvent : searchEvent, firstHop : true,
            replyTo: core.me.destination, receivedOn: core.me.destination,
            originator : core.me))
    }

    void search(String infoHash, String tabTitle) {
        def cardsPanel = builder.getVariable("cards-panel")
        cardsPanel.getLayout().show(cardsPanel, "search window")
        def uuid = UUID.randomUUID()
        Map<String, Object> params = new HashMap<>()
        params["search-terms"] = tabTitle
        params["uuid"] = uuid.toString()
        params["core"] = core
        def group = mvcGroup.createMVCGroup("SearchTab", uuid.toString(), params)
        model.results[uuid.toString()] = group

        def searchEvent = new SearchEvent(searchHash : Base64.decode(infoHash), uuid:uuid,
            oobInfohash: true)
        core.eventBus.publish(new QueryEvent(searchEvent : searchEvent, firstHop : true,
            replyTo: core.me.destination, receivedOn: core.me.destination,
            originator : core.me))
    }

    private int selectedDownload() {
        def downloadsTable = builder.getVariable("downloads-table")
        def selected = downloadsTable.getSelectedRow()
        def sortEvt = mvcGroup.view.lastDownloadSortEvent
        if (sortEvt != null)
            selected = downloadsTable.rowSorter.convertRowIndexToModel(selected)
        selected
    }

    @ControllerAction
    void trustPersonaFromSearch() {
        int selected = builder.getVariable("searches-table").getSelectedRow()
        if (selected < 0)
            return
        Persona p = model.searches[selected].originator
        core.eventBus.publish( new TrustEvent(persona : p, level : TrustLevel.TRUSTED) )
    }

    @ControllerAction
    void distrustPersonaFromSearch() {
        int selected = builder.getVariable("searches-table").getSelectedRow()
        if (selected < 0)
            return
        Persona p = model.searches[selected].originator
        core.eventBus.publish( new TrustEvent(persona : p, level : TrustLevel.DISTRUSTED) )
    }

    @ControllerAction
    void cancel() {
        def downloader = model.downloads[selectedDownload()].downloader
        downloader.cancel()
        model.downloadInfoHashes.remove(downloader.getInfoHash())
        core.eventBus.publish(new UIDownloadCancelledEvent(downloader : downloader))
    }

    @ControllerAction
    void resume() {
        def downloader = model.downloads[selectedDownload()].downloader
        downloader.resume()
        core.eventBus.publish(new UIDownloadResumedEvent())
    }

    @ControllerAction
    void pause() {
        def downloader = model.downloads[selectedDownload()].downloader
        downloader.pause()
        core.eventBus.publish(new UIDownloadPausedEvent())
    }

    private void markTrust(String tableName, TrustLevel level, def list) {
        int row = view.getSelectedTrustTablesRow(tableName)
        if (row < 0)
            return
        builder.getVariable(tableName).model.fireTableDataChanged()
        core.eventBus.publish(new TrustEvent(persona : list[row], level : level))
    }

    @ControllerAction
    void markTrusted() {
        markTrust("distrusted-table", TrustLevel.TRUSTED, model.distrusted)
        model.markTrustedButtonEnabled = false
        model.markNeutralFromDistrustedButtonEnabled = false
    }

    @ControllerAction
    void markNeutralFromDistrusted() {
        markTrust("distrusted-table", TrustLevel.NEUTRAL, model.distrusted)
        model.markTrustedButtonEnabled = false
        model.markNeutralFromDistrustedButtonEnabled = false
    }

    @ControllerAction
    void markDistrusted() {
        markTrust("trusted-table", TrustLevel.DISTRUSTED, model.trusted)
        model.subscribeButtonEnabled = false
        model.markDistrustedButtonEnabled = false
        model.markNeutralFromTrustedButtonEnabled = false
    }

    @ControllerAction
    void markNeutralFromTrusted() {
        markTrust("trusted-table", TrustLevel.NEUTRAL, model.trusted)
        model.subscribeButtonEnabled = false
        model.markDistrustedButtonEnabled = false
        model.markNeutralFromTrustedButtonEnabled = false
    }

    @ControllerAction
    void subscribe() {
        int row = view.getSelectedTrustTablesRow("trusted-table")
        if (row < 0)
            return
        Persona p = model.trusted[row]
        core.muOptions.trustSubscriptions.add(p)
        saveMuWireSettings()
        core.eventBus.publish(new TrustSubscriptionEvent(persona : p, subscribe : true))
        model.subscribeButtonEnabled = false
        model.markDistrustedButtonEnabled = false
        model.markNeutralFromTrustedButtonEnabled = false
    }

    @ControllerAction
    void review() {
        RemoteTrustList list = getSelectedTrustList()
        if (list == null)
            return
        Map<String,Object> env = new HashMap<>()
        env["trustList"] = list
        env["trustService"] = core.trustService
        env["eventBus"] = core.eventBus
        mvcGroup.createMVCGroup("trust-list", env)

    }

    @ControllerAction
    void update() {
        RemoteTrustList list = getSelectedTrustList()
        if (list == null)
            return
        core.eventBus.publish(new TrustSubscriptionEvent(persona : list.persona, subscribe : true))
    }

    @ControllerAction
    void unsubscribe() {
        RemoteTrustList list = getSelectedTrustList()
        if (list == null)
            return
        core.muOptions.trustSubscriptions.remove(list.persona)
        saveMuWireSettings()
        model.subscriptions.remove(list)
        JTable table = builder.getVariable("subscription-table")
        table.model.fireTableDataChanged()
        core.eventBus.publish(new TrustSubscriptionEvent(persona : list.persona, subscribe : false))
    }

    private RemoteTrustList getSelectedTrustList() {
        int row = view.getSelectedTrustTablesRow("subscription-table")
        if (row < 0)
            return null
        model.subscriptions[row]
    }

    void unshareSelectedFile() {
        SharedFile sf = view.selectedSharedFile()
        if (sf == null)
            return
        core.eventBus.publish(new FileUnsharedEvent(unsharedFile : sf))
    }

    void stopWatchingDirectory() {
        String directory = mvcGroup.view.getSelectedWatchedDirectory()
        if (directory == null)
            return
        core.muOptions.watchedDirectories.remove(directory)
        saveMuWireSettings()
        core.eventBus.publish(new DirectoryUnsharedEvent(directory : new File(directory)))

        model.watched.remove(directory)
        builder.getVariable("watched-directories-table").model.fireTableDataChanged()
    }

    void saveMuWireSettings() {
        File f = new File(core.home, "MuWire.properties")
        f.withOutputStream {
            core.muOptions.write(it)
        }
    }

    void mvcGroupInit(Map<String, String> args) {
        application.addPropertyChangeListener("core", {e->
            core = e.getNewValue()
        })
    }
}