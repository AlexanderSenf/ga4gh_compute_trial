##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/02_srtest_allosome/12/wdl
## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/02_srtest_autosome/13/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

version 1.0

import "Tasks02.wdl" as tasks02

workflow SRTestChromosome {
  input {
    File vcf
    String chrom
    String batch
    Int split_size
    String algorithm
    File medianfile
    File splitfile
    File ped_file
    Int? suffix_len
    File male_samples
    File female_samples
    File samples
    Boolean allosome

    String sv_pipeline_docker
    String linux_docker
    String sv_mini_docker
    RuntimeAttr? runtime_attr_split_vcf
    RuntimeAttr? runtime_attr_srtest
    RuntimeAttr? runtime_attr_merge_allo
    RuntimeAttr? runtime_attr_merge_stats
  }
  
  File splitfile_idx = splitfile + ".tbi"

  call tasks02.SplitVCF as SplitVCF {
    input:
      vcf = vcf,
      batch = batch,
      algorithm = algorithm,
      chrom = chrom,
      split_size = split_size,
      suffix_len = select_first([suffix_len, 4]),
      sv_mini_docker = sv_mini_docker,
      runtime_attr_override = runtime_attr_split_vcf
  }

  scatter (split in SplitVCF.split_vcfs) {
    if (allosome) {
      call SRTest as SRTestFemale {
        input:
          vcf = split,
          splitfile = splitfile,
          medianfile = medianfile,
          splitfile_idx = splitfile_idx,
          whitelist = female_samples,
          prefix = basename(split),
          sv_pipeline_docker = sv_pipeline_docker,
          runtime_attr_override = runtime_attr_srtest
      }

      call SRTest as SRTestMale {
        input:
          vcf = split,
          splitfile = splitfile,
          medianfile = medianfile,
          splitfile_idx = splitfile_idx,
          whitelist = male_samples,
          prefix = basename(split),
          sv_pipeline_docker = sv_pipeline_docker,
          runtime_attr_override = runtime_attr_srtest
      }

      call tasks02.MergeAllosomes as MergeAllosomes {
        input:
          male_test = SRTestMale.stats,
          female_test = SRTestFemale.stats,
          chrom = chrom,
          runtime_attr_override = runtime_attr_merge_allo,
          sv_pipeline_docker = sv_pipeline_docker,
          male_only_expr = "females.log_pval.isnull()"
      }
    } 

    if (!allosome) {
      call SRTest as SRTestAutosome {
        input:
          vcf = split,
          splitfile = splitfile,
          medianfile = medianfile,
          splitfile_idx = splitfile_idx,
          whitelist = samples,
          prefix = basename(split),
          sv_pipeline_docker = sv_pipeline_docker,
          runtime_attr_override = runtime_attr_srtest
      }
    }
  }
  
  Array[File?] stats = if allosome then MergeAllosomes.merged_test else SRTestAutosome.stats

  call tasks02.MergeStats as MergeStats {
    input:
      stats = select_all(stats),
      prefix = "${batch}.${algorithm}.${chrom}",
      linux_docker = linux_docker,
      runtime_attr_override = runtime_attr_merge_stats
  }

  output {
    File stats = MergeStats.merged_stats
  }
}

task SRTest {
  input {
    File vcf
    File splitfile
    File medianfile
    File splitfile_idx
    File whitelist
    String prefix
    String sv_pipeline_docker
    RuntimeAttr? runtime_attr_override
  }

  parameter_meta {
    splitfile: {
      localization_optional: true
    }
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
    File stats = "${prefix}.stats"
  }
  command <<<

    set -euo pipefail
    svtk vcf2bed --split-bnd --no-header ~{vcf} test.bed
    awk -v OFS="\t" '{if ($2-250>0){print $1,$2-250,$2+250}else{print $1,0,$2+250}}' test.bed  >> region.bed
    awk -v OFS="\t" '{if ($3-250>0){print $1,$3-250,$3+250}else{print $1,0,$3+250}}' test.bed  >> region.bed
    sort -k1,1 -k2,2n region.bed > region.sorted.bed
    bedtools merge -d 16384 -i region.sorted.bed > region.merged.bed
    GCS_OAUTH_TOKEN=`gcloud auth application-default print-access-token` \
      tabix -R region.merged.bed ~{splitfile} | bgzip -c > SR.txt.gz
    tabix -b 2 -e 2 SR.txt.gz
    svtk sr-test -w 50 --log --index SR.txt.gz.tbi --medianfile ~{medianfile} --samples ~{whitelist} ~{vcf} SR.txt.gz ~{prefix}.stats
  
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
