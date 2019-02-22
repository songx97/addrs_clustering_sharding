package com.btc.paper.data;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

public class BlockProcessor {
	public HashMap<int[],Float> readBlock(String dir, int round){
		File blockDir = new File(dir);
		StringBuilder sb = new StringBuilder();
		JSONObject block_json_obj = null;
		//划分地址簇前后对比
		ArrayList<Integer> beforeClusteredShardStat = new ArrayList<Integer>();
		ArrayList<Integer> afterClusteredShardStat = new ArrayList<Integer>();
		int randShardingCount = 0;
	    int clusteredShardingCount = 0;
		//addr-id Map
		HashMap<String,Integer> addr_id = null;
		// id-cid 映射关系图
	    HashMap<Integer,Integer> id_cid = null;
		int addrsCount = 0;
		int epochAddrCount = 0;		
	    //start 时间
		long start = System.currentTimeMillis();
		if(round > 0) {
			addr_id = this.getAddrIdMap("/home/infosec/sharding_expt/addrid.txt");
			addrsCount = addr_id.size();
			id_cid = this.getIdCid("/home/infosec/sharding_expt/idcid" + (round - 1) + ".txt");
		}
		//注意是LinkedList,记录每个交易内的地址
		LinkedList<HashSet<Integer>> idsList = new LinkedList<HashSet<Integer>>();
		for (File blockData : blockDir.listFiles()) {
			long bstart = System.currentTimeMillis();
			String line = null;
			BufferedReader br = null;
			try {
				br = new BufferedReader(new InputStreamReader(new FileInputStream(blockData)));
				while ((line = br.readLine()) != null)
					sb.append(line);
				block_json_obj = JSON.parseObject(sb.toString());
				sb.setLength(0);
			} catch (FileNotFoundException e) {
				System.out.println(blockData.getName() + "is not found!!");
			} catch (IOException ioe) {
				System.out.println(blockData.getName() + "reading error!!");
			}finally {
				if(br != null) {
					try {
						br.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			JSONArray blocksArr = block_json_obj.getJSONArray("blocks");
			int blocks_size = blocksArr.size();
			for (int i = 0; i < blocks_size; i++) {				
				JSONObject block = blocksArr.getJSONObject(i);
				// 一个块里的交易
				JSONArray txs = block.getJSONArray("tx");
				int tx_size = txs.size();
				System.out.println("交易数:" + tx_size);
				for (int j = 0; j < tx_size; j++){					
				    HashSet<Integer> tx_ids = new HashSet<Integer>();
				    HashSet<Integer> randCid = new HashSet<Integer>();
				    HashSet<Integer> clusteredId = new HashSet<Integer>();
					JSONObject tx = txs.getJSONObject(j);
					// 输入部分的UTXO
					JSONArray inputs = tx.getJSONArray("inputs");
					int input_size = inputs.size();
					for (int k = 0; k < input_size; k++) {
						JSONObject input = inputs.getJSONObject(k);
						JSONObject prev_out = input.getJSONObject("prev_out");
						if (prev_out != null) {
							String addr = prev_out.getString("addr");
							if (addr != null) {
								if(!addr_id.containsKey(addr)) {
									addr_id.put(addr, addrsCount++);
									epochAddrCount++;
								}
								tx_ids.add(addr_id.get(addr));
								if(round > 0) {
									String utxo = prev_out.getString("tx_index") + " " + addr + prev_out.getString("value")
										+ prev_out.getString("script");
									int rcid = this.getRandShards(utxo, 10);
									randCid.add(rcid);
									if(id_cid.containsKey(addr_id.get(addr)))
										clusteredId.add(id_cid.get(addr_id.get(addr)));
									else
										clusteredId.add(rcid);
										 
								}							
							}
						}
					}
					// 输出部分的UTXO
					JSONArray outs = tx.getJSONArray("out");
					int outSize = outs.size();
					for (int k = 0; k < outSize; k++) {
						JSONObject out = outs.getJSONObject(k);
						String addr = out.getString("addr");
						if (addr != null) {
							if(!addr_id.containsKey(addr)) {
								addr_id.put(addr, addrsCount++);
								epochAddrCount++;
							}
							tx_ids.add(addr_id.get(addr));
							if(round > 0) {
								String utxo = out.getString("tx_index") + " " + addr + out.getString("value")
									+ out.getString("script");
								int rcid = this.getRandShards(utxo, 10);
								randCid.add(rcid);
								if(id_cid.containsKey(addr_id.get(addr)))
									clusteredId.add(id_cid.get(addr_id.get(addr)));
								else
									clusteredId.add(rcid);
							}
						}
					}
					beforeClusteredShardStat.add(randCid.size());
					afterClusteredShardStat.add(clusteredId.size());
					idsList.add(tx_ids);
			 }					
	       }
			long bend = System.currentTimeMillis();
			System.out.println("<----->single block cost time:" + (bend-bstart));
		}		
		System.out.print("This round 参与交易的地址数为:" + epochAddrCount);
		//将addr-id映射关系持久化
		this.saveAddrIdMap(addr_id, "/home/infosec/sharding_expt/addrid.txt");
		addr_id.clear();
		id_cid.clear();
		//计算本周期的增量边
		HashMap<int[],Float> edge_weight = new HashMap<int[],Float>();
		for(Iterator<HashSet<Integer>> idsIterator = idsList.iterator();idsIterator.hasNext();) {
			HashSet<Integer> ids = idsIterator.next();
			idsIterator.remove();
			for(Iterator<Integer> idIterator = ids.iterator();idIterator.hasNext();) {
				int id = idIterator.next();
				idIterator.remove();
				for(int iid:ids) {
					int[] key = new int[2];
					int node1 = id;
					int node2 = iid;
					if(id > iid) {
						node1 = iid;
						node2 = id;
				    }
					key[0] = node1;
					key[1] = node2;
					if(edge_weight.containsKey(key))
						edge_weight.put(key, edge_weight.get(key) + 1);
					else
						edge_weight.put(key, (float) 1);
			}
		}
	}
		idsList.clear();
		HashMap<int[],Float> epochGraph = new HashMap<int[],Float>(); 
		//更新历史图
		if(round > 0) {
			BufferedReader ebr = null;
			BufferedWriter ebw = null;
		 try {
			File preEdgeFile = new File("/home/infosec/sharding_expt/" + "edges" + (round-1) +".txt");
			File newEdgeFile = new File("/home/infosec/sharding_expt/edges" + round + ".txt");
			ebr = new BufferedReader(new InputStreamReader(new FileInputStream(preEdgeFile)));
		    ebw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(newEdgeFile)));
		    HashMap<int[],Float> remainingEdges = new HashMap<int[],Float>();
		    String edgeInfo = null;
		    int edgeCounter = 0;		    
		    while((edgeInfo = ebr.readLine()) != null) {
		    	if(edgeInfo.trim().length() < 1)
		    		break;
		    	edgeCounter++;
		    	String[] nodeWeight = edgeInfo.trim().split(" ");
		    	int node1 = Integer.parseInt(nodeWeight[0]);
		    	int node2 = Integer.parseInt(nodeWeight[1]);
		    	float weight = Float.parseFloat(nodeWeight[2]);
		    	int[] edge = new int[] {node1,node2};
		    	if(edge_weight.containsKey(edge)) {
		    		weight = (float) Math.pow(Math.pow(weight, 0.75)+edge_weight.get(edge),0.75);
		    		if(weight > 2)
		    			epochGraph.put(edge, weight);
		    		else
		    			remainingEdges.put(edge, weight);
		    		edge_weight.remove(edge);
		    	}else {
		    		float weightt = (float) (weight*0.75);
		    		if(weight > 0.5)
		    			remainingEdges.put(edge, (float) (weightt));
		    	}
		    	if(edgeCounter >= 10000000) {
		    		for(int[] e:epochGraph.keySet()) {
		    			float w = epochGraph.get(e);
		    			String new_line = e[0] + " " + e[1] + " " + w + "\n";
		    			ebw.write(new_line);
		    		}
		    		for(int[] e:remainingEdges.keySet()) {
		    			float w = remainingEdges.get(e);
		    			String new_line = e[0] + " " + e[1] + " " + w + "\n";
		    			ebw.write(new_line);
		    		}
		    		remainingEdges.clear();
		    		edgeCounter = 0;
		    	}
		    }
		    preEdgeFile.delete();
		    for(int[] nodes:edge_weight.keySet()) {
		    	String line = nodes[0] + " " + nodes[1] + " " + edge_weight.get(nodes) + "\n";
		    	ebw.write(line);
		    	if(edge_weight.get(nodes) <= 2)
		    		edge_weight.remove(nodes);
		    }
		    }catch(FileNotFoundException e) {
		    	e.printStackTrace();
		    }		    
		    catch(IOException e) {
		    	e.printStackTrace();
		    }
		    finally {   	
		    		try {
		    			if(ebr != null) 
		    				ebr.close();
						if(ebw != null)
				    		ebw.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					};		    	
		    }
		    epochGraph.putAll(edge_weight);		    
		    for(int i = 0;i < beforeClusteredShardStat.size();i++) {
		    	randShardingCount += beforeClusteredShardStat.get(i);
		    }
		    for(int i = 0;i < afterClusteredShardStat.size();i++) {
		    	clusteredShardingCount += afterClusteredShardStat.get(i);
		    }
		    System.out.println("round" + round + "在未地址聚类前的跨片数为:" + randShardingCount);
		    System.out.println("round" + round + "地址聚类后的跨片数为:" + clusteredShardingCount);
		}else {
			for(int[] nodes:edge_weight.keySet()) {
				float weight = edge_weight.get(nodes);
				if(edge_weight.get(nodes) > 2)
					epochGraph.put(nodes, weight);
			}
			this.saveInitialEdges(edge_weight);
		}
		long end = System.currentTimeMillis();
		System.out.println("round" + round + "读入数据共用时:(millsecond)" + (end - start));
		return epochGraph;
	}
	//读取地址-id的映射关系，便于分配地址id
	private HashMap<String,Integer> getAddrIdMap(String idCounterPath){
		HashMap<String,Integer> addr_id = new HashMap<String,Integer>();
		FileInputStream fis = null;
		BufferedReader br = null;
		try {
			File idFile = new File("idCounterPath");
			fis= new FileInputStream(idFile);
			br = new BufferedReader(new InputStreamReader(fis));
			String line = null;
			while((line = br.readLine()) != null) {
				if(line.trim().length() < 1)
					break;
				String[] pair = line.trim().split(" ");
				String addr = pair[0];
				int id = Integer.parseInt(pair[0]);
				addr_id.put(addr,id);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}catch(IOException e ) {
			e.printStackTrace();
		}finally {			
			try {
				if(br != null)
					br.close();
				if(fis!=null)
					fis.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}			
		}
		return addr_id;
	}
	private HashMap<Integer,Integer> getIdCid(String idCidPath){
		HashMap<Integer,Integer> id_cid = new  HashMap<Integer,Integer>();
		FileInputStream fis = null;
		BufferedReader br = null;
		try {
			fis= new FileInputStream(idCidPath);
			br = new BufferedReader(new InputStreamReader(fis));
			String line = null;
			while((line = br.readLine()) != null) {
				if(line.trim().length() < 1)
					break;
				String[] pair = line.trim().split(" ");
				int id = Integer.parseInt(pair[0]);
				int cid = Integer.parseInt(pair[0]);
				id_cid.put(id,cid);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}catch(IOException e ) {
			e.printStackTrace();
		}finally {			
			try {
				if(br != null)
					br.close();
				if(fis!=null)
					fis.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}			
		}
		return id_cid;
	}
	//addr-id持久化
	public void saveAddrIdMap(HashMap<String,Integer> addrIdMap,String addrIdPath){
		File addrIdFile = new File(addrIdPath);	
		FileOutputStream fos = null;
		BufferedWriter bw = null;
		try {
			if(!addrIdFile.exists())
				addrIdFile.createNewFile();
			fos= new FileOutputStream(addrIdFile);
			bw = new BufferedWriter(new OutputStreamWriter(fos));
			for(String addr:addrIdMap.keySet()) {
				String line = addr + " " + addrIdMap.get(addr) + "\n";
				bw.write(line);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		}finally {
			
				try {
					if(bw != null)
						bw.close();
					if(fos != null)
						fos.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
			}			
		}
		
	}
	public void saveInitialEdges(HashMap<int[],Float> edges) {
		File edgesFile = new File("/home/infosec/sharding_expt/edges0.txt");
		BufferedWriter bw = null;
		try {
			if(!edgesFile.exists()) {
				edgesFile.createNewFile();
			}
			bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(edgesFile)));
			for(int[] nodes:edges.keySet()) {
				String edge = nodes[0] + " " + nodes[1] + " " + edges.get(nodes) + "\n";
				bw.write(edge);
			}
		}catch(IOException e) {
			e.printStackTrace();
		}finally {
				try {
					if(bw != null)
						bw.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	public Short getRandShards(String utxo, int bitCount) {
			byte[] byteBuffer = null;
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
				messageDigest.update(utxo.getBytes());
				byteBuffer = messageDigest.digest();
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			int j = 0;
			StringBuilder sb = new StringBuilder();
			while (bitCount > 0) {
				if ((bitCount / 8) > 0) {
					sb.append(getBitString(byteBuffer[j], 8));
					j++;
				} else {
					sb.append(getBitString(byteBuffer[j], bitCount));
				}
				bitCount -= 8;
			}
			short order = Short.valueOf(sb.toString(), 2);
		return order;
	}
	public String getBitString(byte b, int count) {
		byte operator = 1;
		StringBuilder sb = new StringBuilder();
		for (int i = 7; i >= 8 - count; i--) {
			byte result = (byte) ((b >>> i) & operator);
			sb.append(result == 0 ? "0" : "1");

		}
		return sb.toString();
	}
}