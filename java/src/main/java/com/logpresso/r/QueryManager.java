package com.logpresso.r;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.logpresso.client.Logpresso;
import com.logpresso.client.Query;
import com.logpresso.client.StreamingResultSet;
import com.logpresso.client.Tuple;

import au.com.bytecode.opencsv.CSVWriter;

public class QueryManager implements StreamingResultSet {
	private static ThreadLocal<SimpleDateFormat> df = new ThreadLocal<SimpleDateFormat>() {
		@Override
		protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		}
	};

	private Logpresso client;
	private int id;
	private boolean isEnd = false;
	private File file;
	private PrintWriter pw;
	private CSVWriter writer;
	private int count1 = 0, count2 = 0;
	private Object flock = new Object();
	private List<String> headers = new ArrayList<String>();
	private String[] fieldOrder;
	private String[] newline = new String[0];

	public QueryManager(Logpresso client, int id, long offset, int limit) throws IOException {
		this.client = client;
		this.id = id;
		this.file = File.createTempFile("rlogdb" + id + "_", ".tmp");
		this.pw = new PrintWriter(file);
		this.writer = new CSVWriter(new StringAppender());

		Map<String, Object> page = client.getResult(id, offset, limit);
		List<Tuple> rows = new ArrayList<Tuple>();
		Object obj = page.get("result");
		if (obj instanceof List) {
			@SuppressWarnings("unchecked")
			List<Object> list = (List<Object>) obj;
			for (Object o : list) {
				if (!(o instanceof Map))
					continue;
				@SuppressWarnings("unchecked")
				Map<String, Object> m = (Map<String, Object>) o;
				rows.add(new Tuple(m));
			}
		}
		onRows(null, rows, true);
	}

	public QueryManager(Logpresso client, String query) throws IOException {
		this.client = client;
		this.id = client.createQuery(query, this);
		this.writer = new CSVWriter(new StringAppender());
		this.file = File.createTempFile("rlogdb" + id + "_", ".tmp");
		this.pw = new PrintWriter(file);
		client.startQuery(id);
	}

	public boolean isEnd() {
		if (isEnd) {
			try {
				Map<String, Object> m = client.getResult(id, 0, 0);
				if (m.containsKey("field_order")) {
					Set<String> copied = new TreeSet<String>(new Comparator<String>() {
						@Override
						public int compare(String o1, String o2) {
							// "_aa" => "_bb" => "aa" => "bb"
							if (o1.startsWith("_"))
								return o2.startsWith("_") ? o1.compareTo(o2) : -1;
							if (o2.startsWith("_"))
								return 1;
							return o1.compareTo(o2);
						}
					});
					copied.addAll(headers);

					Object[] order = (Object[]) m.get("field_order");
					List<String> fieldOrder = new ArrayList<String>();
					for (int i = 0; i < order.length; i++) {
						if (copied.remove(order[i])) {
							fieldOrder.add((String) order[i]);
						}
					}
					fieldOrder.addAll(copied);
					this.fieldOrder = fieldOrder.toArray(new String[0]);
				}
			} catch (Throwable t) {
			}

			try {
				client.removeQuery(id);
			} catch (Throwable t) {
			}
		}
		return isEnd;
	}

	public String filename() throws IOException {
		String filename = file.getAbsolutePath();
		synchronized (flock) {
			pw.close();
			count1 = count2;
			count2 = 0;
			file = File.createTempFile("rlogdb" + id + "_", ".tmp");
			pw = new PrintWriter(file);
		}
		return filename;
	}

	public int rowCount() {
		return count1;
	}

	public String[] headers() {
		return headers.toArray(new String[0]);
	}

	public String[] fieldOrder() {
		return fieldOrder;
	}

	public void stop() {
		try {
			client.stopQuery(id);
		} catch (Throwable t) {
		}
		try {
			client.removeQuery(id);
		} catch (Throwable t) {
		}
		try {
			writer.close();
		} catch (Throwable t) {
		}
		file.delete();
	}

	@Override
	public void onRows(Query query, List<Tuple> rows, boolean last) {
		for (Tuple row : rows) {
			Map<String, Object> m = row.toMap();
			if (!headers.containsAll(m.keySet())) {
				Set<String> newKeys = new HashSet<String>(m.keySet());
				newKeys.removeAll(headers);
				headers.addAll(newKeys);
				newline = new String[headers.size()];
			}

			int idx = 0;
			for (String key : headers) {
				Object value = m.get(key);
				if (value != null) {
					if (value instanceof Date)
						value = df.get().format(value);
					else
						value = value.toString();
				}

				newline[idx++] = (String) value;
			}
			writer.writeNext(newline);
		}

		if (last) {
			try {
				writer.close();
			} catch (IOException e) {
			}
			isEnd = true;
		}
	}

	private class StringAppender extends PrintWriter {
		public StringAppender() {
			super(new OutputStream() {
				@Override
				public void write(int b) throws IOException {
				}
			});
		}

		@Override
		public void write(String s, int off, int len) {
			synchronized (lock) {
				synchronized (flock) {
					pw.write(s, off, len);
					count2++;
				}
			}
		}

		@Override
		public void close() {
			super.close();
			pw.close();
		}
	}
}
