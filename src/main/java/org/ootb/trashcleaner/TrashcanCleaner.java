package org.ootb.trashcleaner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.transaction.UserTransaction;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TrashcanCleaner {

	private static Log logger = LogFactory.getLog(TrashcanCleaner.class);
    protected static final int DEFAULT_DAYS_TO_KEEP = -1;
    protected static final int DEFAULT_DELETE_BATCH_COUNT = 1000;
    private static final long DAYS_TO_MILLIS = 1000 * 60 * 60 * 24;

    private NodeService nodeService;
    private String archiveStoreUrl = "archive://SpacesStore";
    private int deleteBatchCount = DEFAULT_DELETE_BATCH_COUNT;
    private int daysToKeep = DEFAULT_DAYS_TO_KEEP;

    /**
     * 
     * It will use the default values for <b>deleteBatchCount</b> and
     * <b>daysToKeep</b>, 1000 and -1.
     * 
     * @param nodeService
     */
    public TrashcanCleaner(NodeService nodeService)
    {
            this.nodeService = nodeService;
    }

    /**
     * 
     * If you need to set explicit values different than default for
     * <b>deleteBatchCount</b> and <b>daysToKeep</b>.
     * 
     * @param nodeService
     * @param deleteBatchCount
     * @param daysToKeep
     */
    public TrashcanCleaner(NodeService nodeService, int deleteBatchCount,
            int daysToKeep)
    {
            this(nodeService);
            this.deleteBatchCount = deleteBatchCount;
            this.daysToKeep = daysToKeep;
    }

    /**
     * 
     * In case you also need to set a different store for <b>archiveStoreUrl</b>
     * than the default archive://SpacesStore.
     * 
     * @param nodeService
     * @param archiveStoreUrl
     * @param deleteBatchCount
     * @param daysToKeep
     */
    public TrashcanCleaner(NodeService nodeService, String archiveStoreUrl,
            int deleteBatchCount, int daysToKeep)
    {
            this(nodeService, deleteBatchCount, daysToKeep);
            this.archiveStoreUrl = archiveStoreUrl;
    }

    /**
     * 
     * The method that will clean the specified <b>archiveStoreUrl</b> to the
     * limits defined by the values set for <b>deleteBatchCount</b> and
     * <b>daysToKeep</b>.
     * 
     */
    public void clean()
    {
            List<NodeRef> nodes = getBatchToDelete();

            if (logger.isDebugEnabled())
            {
                    logger.debug(String.format("Number of nodes to delete: %s",
                            nodes.size()));
            }

            deleteNodes(nodes);

            if (logger.isDebugEnabled())
            {
                    logger.debug("Nodes deleted");
            }
    }

    /**
     * 
     * It deletes the {@link java.util.List List} of
     * {@link org.alfresco.service.cmr.repository.NodeRef NodeRef} received as
     * argument.
     * 
     * @param nodes
     */
    private void deleteNodes(List<NodeRef> nodes)
    {
            for (int i = nodes.size(); i > 0; i--)
            {
                    nodeService.deleteNode(nodes.get(i - 1));
            }
    }

    /**
     * 
     * It returns the {@link java.util.List List} of
     * {@link org.alfresco.service.cmr.repository.NodeRef NodeRef} of the
     * archive store set to be deleted according to configuration for
     * <b>deleteBatchCount</b> and <b>daysToKeep</b>.
     * 
     * @return
     */
    private List<NodeRef> getBatchToDelete()
    {
            List<ChildAssociationRef> childAssocs = getTrashcanChildAssocs();
            List<NodeRef> nodes = new ArrayList<NodeRef>(deleteBatchCount);
            if (logger.isDebugEnabled())
            {
                    logger.debug(String.format("Found %s nodes on trashcan",
                            childAssocs.size()));
            }
            return fillBatchToDelete(nodes, childAssocs);
    }

    /**
     * 
     * It will fill up a {@link java.util.List List} of
     * {@link org.alfresco.service.cmr.repository.NodeRef NodeRef} from all the
     * {@link org.alfresco.service.cmr.repository.ChildAssociationRef
     * ChildAssociationRef} of the archive store set, according to the limit
     * parameters: <b>deleteBatchCount</b> and <b>daysToKeep</b>.
     * 
     * @param batch
     * @param trashChildAssocs
     * @return
     */
    private List<NodeRef> fillBatchToDelete(List<NodeRef> batch,
            List<ChildAssociationRef> trashChildAssocs)
    {
            for (int j = trashChildAssocs.size(); j > 0
                    && batch.size() < deleteBatchCount; j--)
            {
                    ChildAssociationRef childAssoc = trashChildAssocs.get(j - 1);
                    NodeRef childRef = childAssoc.getChildRef();
                    if (olderThanDaysToKeep(childRef))
                    {
                            batch.add(childRef);
                    }
            }
            return batch;
    }

    /**
     * 
     * It will return all
     * {@link org.alfresco.service.cmr.repository.ChildAssociationRef
     * ChildAssociationRef} of the archive store set.
     * 
     * @return
     */
    private List<ChildAssociationRef> getTrashcanChildAssocs()
    {
            StoreRef archiveStore = new StoreRef(archiveStoreUrl);
            NodeRef archiveRoot = nodeService.getRootNode(archiveStore);
            List<ChildAssociationRef> allChilds= nodeService.getChildAssocs(archiveRoot);
            return filterArchiveUsers(allChilds);
    }
    
    
    /**
     * 
     * It returns the number of nodes present on trashcan.
     * 
     * @return
     */
    public long getNumberOfNodesInTrashcan()
    {
            return getTrashcanChildAssocs().size();

    }
    
    /**
     * 
     * Don't include on list of nodes to be deleted the archiveuser node types.
     * 
     * @return
     */
    private List<ChildAssociationRef> filterArchiveUsers(List<ChildAssociationRef> allChilds){
            List<ChildAssociationRef> childs=new ArrayList<ChildAssociationRef>();
            for(ChildAssociationRef childAssoc:allChilds){
                    NodeRef child=childAssoc.getChildRef();
                    if(!ContentModel.TYPE_ARCHIVE_USER.equals(nodeService.getType(child))){
                            childs.add(childAssoc);
                    }
            }
            return childs;
    }

    /**
     * 
     * It checks if the archived node has been archived since longer than
     * <b>daysToKeep</b>. If <b>daysToKeep</b> is 0 or negative it will return
     * always true.
     * 
     * @param node
     * @return
     */
    private boolean olderThanDaysToKeep(NodeRef node)
    {
            if (daysToKeep <= 0)
                    return true;
            Date archivedDate = (Date) nodeService.getProperty(node,
                    ContentModel.PROP_ARCHIVED_DATE);
            long archivedDateValue=0;
            if(archivedDate!=null)
                    archivedDateValue=archivedDate.getTime();
            return daysToKeep * DAYS_TO_MILLIS < System.currentTimeMillis()
                    - archivedDateValue;
    }
}
