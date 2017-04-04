/**
 * TODO: updating components and assets' lastUpdated does not work
 */
import org.joda.time.DateTime
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.manager.RepositoryManager
import org.sonatype.nexus.repository.search.SearchFacet
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.Component
import org.sonatype.nexus.repository.storage.StorageFacet
import org.sonatype.nexus.repository.storage.StorageTx

RepositoryManager repoManager = repository.repositoryManager
Repository releasesRepo = repoManager.get("maven-releases")
StorageTx storageTx = releasesRepo.facet(StorageFacet).txSupplier().get()
try {
    storageTx.begin()

    log.info("Querying components...")
    Map<String, String> params = new HashMap<String, String>()
    params.put("groupId", "nexus.tests")
    params.put("artifactId", "tests")
    Iterable<Component> testArtifacts = storageTx.findComponents("group = :groupId and name = :artifactId", params, null, null)
    int count = 0
    DateTime lastUpdated
    for (Component component : testArtifacts) {
        log.info("Found one: {}:{}:{}", component.group(), component.name(), component.version())

        lastUpdated = component.lastUpdated().minusDays(daysSpread * count)
        for (Asset asset : storageTx.browseAssets(component)) {
            asset.lastUpdated(lastUpdated)
            storageTx.saveAsset(asset)
        }
        component.lastUpdated(lastUpdated)
        storageTx.saveComponent(component)

        log.info("new component update: {}", lastUpdated)
        count++
    }

    storageTx.commit()
}
catch (Exception e) {
    storageTx.rollback()
}
finally {
    storageTx.close()
}

releasesRepo.facet(SearchFacet).rebuildIndex()
