package com.ootb.trashcan;

import org.alfresco.repo.security.authentication.AuthenticationComponent;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.schedule.AbstractScheduledLockedJob;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.transaction.TransactionService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class TrashcanCleanerJob extends AbstractScheduledLockedJob {
	protected NodeService nodeService;
	protected TransactionService transactionService;
	protected AuthenticationComponent authenticationComponent;
	private int deleteBatchCount;
	private int daysToKeep;
	
	@Override
	public void executeJob(JobExecutionContext jobContext) throws JobExecutionException {
		setUp(jobContext);
		authenticationComponent.setSystemUserAsCurrentUser();
		cleanInTransaction();
	}

	/**
	 * 
	 * This method instantiates the
	 * {@link org.alfresco.trashcan.TrashcanCleaner TrashcanCleaner} and calls
	 * the execution of the <b>clean</b> method inside a transaction.
	 */
	private void cleanInTransaction()
	{
		RetryingTransactionCallback<Object> txnWork = new RetryingTransactionCallback<Object>()
		{
			public Object execute() throws Exception
			{
				TrashcanCleaner cleaner = new TrashcanCleaner(nodeService,
				        deleteBatchCount, daysToKeep);
				cleaner.clean();
				return null;
			}
		};
		transactionService.getRetryingTransactionHelper().doInTransaction(
		        txnWork);
	}
	
	/**
	 * 
	 * Extracts the necessary services and configuration for trashcan cleaning:
	 * <b>trashcan.deleteBatchCount</b> and <b>trashcan.daysToKeep</b>. The
	 * services needed to be injected are the <b>nodeService</b>,
	 * <b>transactionService</b> and <b>authenticationComponent</b>. Since iots
	 * an extension of {@link org.alfresco.schedule.AbstractScheduledLockedJob
	 * AbstractScheduledLockedJob} it should also receive reference to the
	 * service {@link org.alfresco.repo.lock.JobLockService jobLockService}.
	 * 
	 * @param jobContext
	 */
	private void setUp(JobExecutionContext jobContext)
	{
		nodeService = (NodeService) jobContext.getJobDetail().getJobDataMap()
		        .get("nodeService");
		transactionService = (TransactionService) jobContext.getJobDetail()
		        .getJobDataMap().get("transactionService");
		authenticationComponent = (AuthenticationComponent) jobContext
		        .getJobDetail().getJobDataMap().get("authenticationComponent");
		daysToKeep = getSetupValue("trashcan.daysToKeep",
		        TrashcanCleaner.DEFAULT_DAYS_TO_KEEP, jobContext);
		deleteBatchCount = getSetupValue("trashcan.deleteBatchCount",
		        TrashcanCleaner.DEFAULT_DELETE_BATCH_COUNT, jobContext);

	}
	
	/**
	 * 
	 * Extracts the specified parameter value from the
	 * {@link org.quartz.JobExecutionContext jobContext}. If it is not specified
	 * returns the corresponding default value.
	 * 
	 * @param parameterName
	 * @param defaultValue
	 * @param jobContext
	 * @return
	 */
	private static int getSetupValue(String parameterName, int defaultValue,
	        JobExecutionContext jobContext)
	{
		String parameterValue = (String) jobContext.getJobDetail()
		        .getJobDataMap().get(parameterName);
		return parameterValue != null && !parameterValue.trim().equals("") ? Integer
		        .parseInt(parameterValue) : defaultValue;
	}
}
