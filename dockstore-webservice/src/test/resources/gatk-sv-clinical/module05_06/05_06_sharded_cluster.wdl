version 1.0
# based on snapshot 6
# https://portal.firecloud.org/#methods/Talkowski-SV/04_sharded_vcfcluster/6/wdl

# Copyright (c) 2018 Talkowski Lab

# Contact Ryan Collins <rlcollins@g.harvard.edu>

# Distributed under terms of the MIT License

import "05_06_structs.wdl"
import "05_06_common_mini_tasks.wdl" as MiniTasks

# Workflow to shard a filtered vcf & run vcfcluster (sub-sub-sub workflow)
workflow ShardedCluster {
  input {
    File vcf
    File? vcf_idx
    Int dist
    Float frac
    Int max_shards
    Int min_per_shard
    String prefix
    String contig
    String sv_type
    Float sample_overlap
    File? blacklist
    File? blacklist_idx
    Int sv_size
    Array[String] sv_types

    String sv_pipeline_docker
    String sv_base_mini_docker

    # overrides for local tasks
    RuntimeAttr? runtime_override_shard_vcf_precluster
    RuntimeAttr? runtime_override_svtk_vcf_cluster
    RuntimeAttr? runtime_override_get_vcf_header_with_members_info_line

    # overrides for MiniTasks
    RuntimeAttr? runtime_override_concat_shards
  }
  
  String sv_type_prefix = prefix + "." + contig + "." + sv_type

  #New as of November 2, 2018: perform sharding and return list of variant IDs
  # for each shard, rather than VCF shards themselves, which should dramatically
  # improve speed of sharding task (previously took 1-6 hours for 14k samples in
  # gnomAD v2)
  call ShardVcfPrecluster {
    input:
      vcf=vcf,
      vcf_idx=vcf_idx,
      dist=dist,
      frac=frac,
      max_shards=max_shards,
      min_per_shard=min_per_shard,
      prefix=sv_type_prefix,
      sv_pipeline_docker=sv_pipeline_docker,
      runtime_attr_override=runtime_override_shard_vcf_precluster
  }

  #Run vcfcluster per shard
  scatter (VIDs_list in ShardVcfPrecluster.VID_list_shards) {
    call SvtkVcfCluster {
      input:
        vcf=vcf,
        VIDs=VIDs_list,
        prefix=sv_type_prefix,
        dist=dist,
        frac=frac,
        sample_overlap=sample_overlap,
        blacklist=blacklist,
        blacklist_idx=blacklist_idx,
        svsize=sv_size,
        sv_types=sv_types,
        sv_pipeline_docker=sv_pipeline_docker,
        runtime_attr_override=runtime_override_svtk_vcf_cluster
    }
  }
  if (length(SvtkVcfCluster.clustered_vcf) == 0) {
    call GetVcfHeaderWithMembersInfoLine {
      input:
        vcf_gz=vcf,
        prefix=sv_type_prefix,
        sv_base_mini_docker=sv_base_mini_docker,
        runtime_attr_override=runtime_override_get_vcf_header_with_members_info_line
    }
  }

  #Merge shards per svtype
  Array[File] clustered_vcfs = if length(SvtkVcfCluster.clustered_vcf) > 0 then SvtkVcfCluster.clustered_vcf else select_all([GetVcfHeaderWithMembersInfoLine.out])
  call MiniTasks.ConcatVcfs as ConcatShards {
    input:
      vcfs=clustered_vcfs,
      outfile_prefix=sv_type_prefix,
      index_output=false,
      sv_base_mini_docker=sv_base_mini_docker,
      runtime_attr_override=runtime_override_concat_shards
  }

  #Output
  output {
    File clustered_vcf = ConcatShards.concat_vcf
  }
}

# Adds MEMBERS definition to header (workaround for when VIDs_list is empty)
task GetVcfHeaderWithMembersInfoLine {
  input {
    File vcf_gz
    String prefix
    String sv_base_mini_docker
    RuntimeAttr? runtime_attr_override
  }
  RuntimeAttr runtime_default = object {
    mem_gb: 1,
    disk_gb: 10,
    cpu_cores: 1,
    preemptible_tries: 3,
    max_retries: 1,
    boot_disk_gb: 10
  }
  RuntimeAttr runtime_override = select_first([runtime_attr_override, runtime_default])

  runtime {
    memory: "~{select_first([runtime_override.mem_gb, runtime_default.mem_gb])} GiB"
    disks: "local-disk ~{select_first([runtime_override.disk_gb, runtime_default.disk_gb])} HDD"
    cpu: select_first([runtime_override.cpu_cores, runtime_default.cpu_cores])
    preemptible: select_first([runtime_override.preemptible_tries, runtime_default.preemptible_tries])
    maxRetries: select_first([runtime_override.max_retries, runtime_default.max_retries])
    docker: sv_base_mini_docker
    bootDiskSizeGb: select_first([runtime_override.boot_disk_gb, runtime_default.boot_disk_gb])
  }

  command <<<
    set -euo pipefail
    gunzip -c ~{vcf_gz} | grep "^##" > header
    echo "##INFO=<ID=MEMBERS,Number=.,Type=String,Description=\"IDs of cluster's constituent records.\">" >> header
    gunzip -c ~{vcf_gz} | grep "^#" | grep -v "^##" >> header
    bgzip -c header > ~{prefix}.members.vcf.gz
  >>>

  output {
    File out = "~{prefix}.members.vcf.gz"
  }
}

#Intelligently shard a VCF for parallelized clustering
task ShardVcfPrecluster {
  input {
    File vcf
    File? vcf_idx
    Int dist
    Float frac
    Int max_shards
    Int min_per_shard
    String prefix
    String sv_pipeline_docker
    RuntimeAttr? runtime_attr_override
  }

  # generally assume working disk size is ~2 * inputs, and outputs are ~2 *inputs, and inputs are not removed
  # generally assume working memory is ~3 * inputs
  Float shard_size = size(select_all([vcf, vcf_idx]), "GiB")
  Float base_disk_gb = 10.0
  Float base_mem_gb = 2.0
  Float input_mem_scale = 3.0
  Float input_disk_scale = 5.0
  RuntimeAttr runtime_default = object {
    mem_gb: base_mem_gb + shard_size * input_mem_scale,
    disk_gb: ceil(base_disk_gb + shard_size * input_disk_scale),
    cpu_cores: 1,
    preemptible_tries: 3,
    max_retries: 1,
    boot_disk_gb: 10
  }
  RuntimeAttr runtime_override = select_first([runtime_attr_override, runtime_default])
  runtime {
    memory: "~{select_first([runtime_override.mem_gb, runtime_default.mem_gb])} GiB"
    disks: "local-disk ~{select_first([runtime_override.disk_gb, runtime_default.disk_gb])} HDD"
    cpu: select_first([runtime_override.cpu_cores, runtime_default.cpu_cores])
    preemptible: select_first([runtime_override.preemptible_tries, runtime_default.preemptible_tries])
    maxRetries: select_first([runtime_override.max_retries, runtime_default.max_retries])
    docker: sv_pipeline_docker
    bootDiskSizeGb: select_first([runtime_override.boot_disk_gb, runtime_default.boot_disk_gb])
  }

  command <<<
    set -eu -o pipefail

    ~{if defined(vcf_idx) then "" else "tabix -f -p vcf ~{vcf}"}

    /opt/sv-pipeline/04_variant_resolution/scripts/shardVCF_preClustering_part1.sh \
      -D ~{dist} \
      -R ~{frac} \
      -L ~{min_per_shard} \
      -S ~{max_shards} \
      -P ~{prefix} \
      ~{vcf}
  >>>

  output {
    Array[File] VID_list_shards = glob("*.VIDs.list")
  }
}


#Run svtk vcfcluster
task SvtkVcfCluster {
  input {
    File vcf
    File VIDs
    String prefix
    Int dist
    Float frac
    Float sample_overlap
    File? blacklist
    File? blacklist_idx
    Int svsize
    Array[String] sv_types
    String sv_pipeline_docker
    RuntimeAttr? runtime_attr_override
  }

  # generally assume working disk size is ~2 * inputs, and outputs are ~2 *inputs, and inputs are not removed
  # generally assume working memory is ~3 * inputs
  Float shard_size = size(select_all([vcf, VIDs, blacklist, blacklist_idx]), "GiB")
  Float base_disk_gb = 10.0
  Float base_mem_gb = 2.0
  Float input_mem_scale = 3.0
  Float input_disk_scale = 5.0
  RuntimeAttr runtime_default = object {
    mem_gb: base_mem_gb + shard_size * input_mem_scale,
    disk_gb: ceil(base_disk_gb + shard_size * input_disk_scale),
    cpu_cores: 1,
    preemptible_tries: 3,
    max_retries: 1,
    boot_disk_gb: 10
  }
  RuntimeAttr runtime_override = select_first([runtime_attr_override, runtime_default])
  runtime {
    memory: "~{select_first([runtime_override.mem_gb, runtime_default.mem_gb])} GiB"
    disks: "local-disk ~{select_first([runtime_override.disk_gb, runtime_default.disk_gb])} HDD"
    cpu: select_first([runtime_override.cpu_cores, runtime_default.cpu_cores])
    preemptible: select_first([runtime_override.preemptible_tries, runtime_default.preemptible_tries])
    maxRetries: select_first([runtime_override.max_retries, runtime_default.max_retries])
    docker: sv_pipeline_docker
    bootDiskSizeGb: select_first([runtime_override.boot_disk_gb, runtime_default.boot_disk_gb])
  }

  command <<<
    set -eu -o pipefail
    
    # Filter vcf to only include passed variant IDs
    /opt/sv-pipeline/04_variant_resolution/scripts/filter_vcf.sh \
      "~{vcf}" \
      "{ fgrep -wf ~{VIDs} || true; }" \
      input.vcf.gz

    # Don't generate random characters for vcf name, it produces problems with caching on cromwell
    # You *could* pass a seed like so:
    # INPUT_HASH=$(tr -d '/+' < <(openssl enc -a -aes-256-ctr -pass pass:"$SEED" -nosalt </dev/zero 2>/dev/null) | head -c16)
    # But if you hash filtered input vcf, you accomplish the same goal of avoiding similar-named files in the loop,
    # without introducing randomness:
    INPUT_HASH=$(md5sum input.vcf.gz | awk '{print $1}')
    # concat prefix and hash to create unique vcf name:
    VCF_NAME="~{prefix}-$INPUT_HASH"

    ~{if defined(blacklist) && !defined(blacklist_idx) then "tabix -f -p bed ~{blacklist}" else ""}

    #Run clustering
    echo "input.vcf.gz" > unclustered_vcfs.list
    svtk vcfcluster unclustered_vcfs.list $VCF_NAME.vcf \
      -d ~{dist} \
      -f ~{frac} \
      ~{if defined(blacklist) then "-x ~{blacklist}" else ""} \
      -z ~{svsize} \
      -p ~{prefix} \
      -t ~{sep=',' sv_types} \
      -o ~{sample_overlap} \
      --preserve-ids \
      --preserve-genotypes \
      --preserve-header

    # compress the vcf
    bgzip -f $VCF_NAME.vcf
  >>>

  output {
    # need to use glob since cromwell will not be aware of the value of INPUT hash
    File clustered_vcf = glob("~{prefix}*.vcf.gz")[0]
  }
}
