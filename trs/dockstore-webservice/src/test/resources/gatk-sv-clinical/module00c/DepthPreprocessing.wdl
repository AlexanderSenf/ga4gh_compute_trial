##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/00_depth_preprocessing_natormops/5/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

version 1.0

import "Structs.wdl"

workflow MergeDepth {
  input {
    Array[String]+ samples
    Array[File] genotyped_segments_vcfs
    Array[File] contig_ploidy_calls
    File std_cnmops_del
    File std_cnmops_dup
    String batch
    String sv_mini_docker
    String sv_pipeline_docker
    Int gcnv_qs_cutoff
    RuntimeAttr? runtime_attr_merge_sample
    RuntimeAttr? runtime_attr_merge_set
    RuntimeAttr? runtime_attr_convert_gcnv
  }

  scatter (i in range(length(samples))) {
    call GcnvVcfToBed {
      input:
        sample_id = samples[i],
        vcf = genotyped_segments_vcfs[i],
        contig_ploidy_call_tar = contig_ploidy_calls[i],
        sv_pipeline_docker = sv_pipeline_docker,
        qs_cutoff = gcnv_qs_cutoff,
        runtime_attr_override = runtime_attr_convert_gcnv
    }
  }

  scatter (i in range(length(samples))) {
    call MergeSample as MergeSample_del {
      input:
        sample_id = samples[i],
        gcnv = GcnvVcfToBed.del_bed[i],
        cnmops = std_cnmops_del,
        sv_mini_docker = sv_mini_docker,
        runtime_attr_override = runtime_attr_merge_sample
    }
  }

  scatter (i in range(length(samples))) {
    call MergeSample as MergeSample_dup {
      input:
        sample_id = samples[i],
        gcnv = GcnvVcfToBed.dup_bed[i],
        cnmops = std_cnmops_dup,
        sv_mini_docker = sv_mini_docker,
        runtime_attr_override = runtime_attr_merge_sample
    }
  }

  call MergeSet as MergeSet_del {
    input:
      beds = MergeSample_del.sample_bed,
      svtype = "DEL",
      batch = batch,
      sv_mini_docker = sv_mini_docker,
      runtime_attr_override = runtime_attr_merge_set
  }

  call MergeSet as MergeSet_dup {
    input:
      beds = MergeSample_dup.sample_bed,
      svtype = "DUP",
      batch = batch,
      sv_mini_docker = sv_mini_docker,
      runtime_attr_override = runtime_attr_merge_set
  }
  output{
    File del = MergeSet_del.out
    File del_index = MergeSet_del.out_idx
    File dup = MergeSet_dup.out
    File dup_index = MergeSet_dup.out_idx
  }
}

task GcnvVcfToBed {
  input {
    File vcf
    File contig_ploidy_call_tar
    String sample_id
    String sv_pipeline_docker
    Int qs_cutoff
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1,
    mem_gb: 3.75,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File del_bed = "~{sample_id}.del.bed"
    File dup_bed = "~{sample_id}.dup.bed"
  }
  command <<<

    set -e
    tar xzf ~{contig_ploidy_call_tar}
    tabix ~{vcf}
    python /opt/WGD/bin/convert_gcnv.py \
      --cutoff ~{qs_cutoff} \
      contig_ploidy.tsv \
      ~{vcf} \
      ~{sample_id}

  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_pipeline_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }
}

task MergeSample {
  input {
    File gcnv
    File cnmops
    String sample_id
    String sv_mini_docker
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1, 
    mem_gb: 3.75,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File sample_bed = "~{sample_id}_merged.bed"
  }
  command <<<

    set -euo pipefail
    zcat ~{cnmops} | egrep "~{sample_id}" > cnmops.cnv
    cat ~{gcnv} cnmops.cnv | sort -k1,1V -k2,2n > ~{sample_id}.bed
    bedtools merge -i ~{sample_id}.bed -d 0 -c 4,5,6,7 -o distinct > ~{sample_id}_merged.bed
    
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_mini_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }

}

task MergeSet {
  input {
    Array[File] beds
    String svtype
    String batch
    String sv_mini_docker
    RuntimeAttr? runtime_attr_override
  }

  RuntimeAttr default_attr = object {
    cpu_cores: 1, 
    mem_gb: 3.75,
    disk_gb: 10,
    boot_disk_gb: 20,
    preemptible_tries: 3,
    max_retries: 1
  }
  RuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File out = "~{batch}.~{svtype}.bed.gz"
    File out_idx = "~{batch}.~{svtype}.bed.gz.tbi"
  }
  command <<<

    set -euo pipefail

    # TODO: fail fast if localization failed. This is a Cromwell issue.
    while read file; do
      if [ ! -f $file ]; then
        echo "Localization failed: ${file}" >&2
        exit 1
      fi
    done < ~{write_lines(beds)};

    zcat -f ~{sep=' ' beds} \
      | sort -k1,1V -k2,2n \
      | awk -v OFS="\t" -v svtype=~{svtype} -v batch=~{batch} '{$4=batch"_"svtype"_"NR; print}' \
      | cat <(echo -e "#chr\\tstart\\tend\\tname\\tsample\\tsvtype\\tsources") - \
      | bgzip -c > ~{batch}.~{svtype}.bed.gz;
    tabix -p bed ~{batch}.~{svtype}.bed.gz
		
  >>>
  runtime {
    cpu: select_first([runtime_attr.cpu_cores, default_attr.cpu_cores])
    memory: select_first([runtime_attr.mem_gb, default_attr.mem_gb]) + " GiB"
    disks: "local-disk " + select_first([runtime_attr.disk_gb, default_attr.disk_gb]) + " HDD"
    bootDiskSizeGb: select_first([runtime_attr.boot_disk_gb, default_attr.boot_disk_gb])
    docker: sv_mini_docker
    preemptible: select_first([runtime_attr.preemptible_tries, default_attr.preemptible_tries])
    maxRetries: select_first([runtime_attr.max_retries, default_attr.max_retries])
  }

}

