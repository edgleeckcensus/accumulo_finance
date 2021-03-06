package mapreduce;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.apache.accumulo.core.cli.MapReduceClientOnRequiredTable;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.mapreduce.AccumuloOutputFormat;
import org.apache.accumulo.core.data.Mutation;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.beust.jcommander.Parameter;

import nasdaq.NasdaqListingFetcher;

public class YahooGetDailyStockQuotes extends Configured implements Tool {

	public static final Opts opts = new Opts();
	public static Connector accumuloConnector;
	public static void main(String[] args) throws Exception {
		int exitCode = ToolRunner.run(new Configuration(), new YahooGetDailyStockQuotes(), args);
		System.exit(exitCode);
	}
	
	static class Opts extends MapReduceClientOnRequiredTable {
		@Parameter(names = "--input", required = true)
		String input;
	}
	
	@Override
	public int run(String[] args) throws Exception {
				
		
		// setup the job
		Configuration CONF = getConf();
		Job job = Job.getInstance(CONF);
		job.setJobName("Process All Stocks");
		job.setJarByClass(ProcessStockFiles.class);
		job.setMapperClass(YahooGetDailStockQuotesMapper.class);
		job.setInputFormatClass(NLineInputFormat.class);
		job.setOutputFormatClass(AccumuloOutputFormat.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Mutation.class);
		job.setNumReduceTasks(0);
		
		opts.parseArgs(job.getJobName(), args, new Object[]{});
		opts.setAccumuloConfigs(job);
		
		NasdaqListingFetcher fetcher = new NasdaqListingFetcher();
		BufferedReader reader = new BufferedReader(fetcher.getReaderFromURL());
		
		FileSystem fs = FileSystem.get(CONF);
		String path = "hdfs://127.0.0.1:9000/user/egleeck/input/nasdaq_latest.txt";
		Path filePath = new Path(path);
		
		fs.delete(filePath, false);
		
		OutputStream os = fs.create(filePath);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
		
		String line = null;
		while((line = reader.readLine()) != null) {
			writer.write(line);
			writer.newLine();
		}
		
		writer.close();
		reader.close();
		
		
		NLineInputFormat.addInputPath(job, new Path(opts.input));
		NLineInputFormat.setNumLinesPerSplit(job, 10);
		
		job.getConfiguration().set("tablename", opts.getTableName());
		
		return job.waitForCompletion(true) ? 0 : 1;
	}

}
