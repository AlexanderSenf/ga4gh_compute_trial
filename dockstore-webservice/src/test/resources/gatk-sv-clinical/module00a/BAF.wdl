##########################################################################################

## Base script:   https://portal.firecloud.org/#methods/Talkowski-SV/BAF/1/wdl

## Github commit: talkowski-lab/gatk-sv-v1:<ENTER HASH HERE IN FIRECLOUD>

##########################################################################################

## Generate BAF file ##

version 1.0

import "Structs.wdl" alias RuntimeAttr as BAFRuntimeAttr

workflow BAF {
  input {
    Array[File] gvcfs
    Array[File] gvcf_indexes
    Array[String] samples
    File unpadded_intervals_file
    File dbsnp_vcf
    File chrom_file
    File ref_fasta
    File ref_fasta_index
    File ref_dict
    String batch
    String gatk_docker
    String sv_mini_docker
    String sv_pipeline_docker
    BAFRuntimeAttr? runtime_attr_merge_vcfs
    BAFRuntimeAttr? runtime_attr_baf_gen
    BAFRuntimeAttr? runtime_attr_gather
  }

  Array[Array[String]] chroms = read_tsv(chrom_file)

  Int num_of_original_intervals = length(read_lines(unpadded_intervals_file))
  Int num_gvcfs = length(gvcfs)

  # Make a 2.5:1 interval number to samples in callset ratio interval list
  Int possible_merge_count = floor(num_of_original_intervals / num_gvcfs / 2.5)
  Int merge_count = if possible_merge_count > 1 then possible_merge_count else 1

  call DynamicallyCombineIntervals {
    input:
      intervals = unpadded_intervals_file,
      merge_count = merge_count,
      preemptible = 3
  }

  Array[String] unpadded_intervals = read_lines(DynamicallyCombineIntervals.output_intervals)

  scatter (idx in range(length(unpadded_intervals))) {
    call ImportGVCFs {
      input:
        sample_names = samples,
        input_gvcfs = gvcfs,
        input_gvcfs_indices = gvcf_indexes,
        interval = unpadded_intervals[idx],
        workspace_dir_name = "genomicsdb",
        disk_size = 200,
        batch_size = 50,
        docker = gatk_docker,
        gatk_path = "/gatk/gatk",
        preemptible = 3
    }
    call GenotypeGVCFs {
      input:
        workspace_tar = ImportGVCFs.output_genomicsdb,
        interval = unpadded_intervals[idx],
        output_vcf_filename = "~{batch}.~{idx}.vcf.gz",
        ref_fasta = ref_fasta,
        ref_fasta_index = ref_fasta_index,
        ref_dict = ref_dict,
        dbsnp_vcf = dbsnp_vcf,
        disk_size = 200,
        docker = gatk_docker,
        gatk_path = "/gatk/gatk",
        preemptible = 3
    }
    call GenerateBAF {
      input:
        vcf = GenotypeGVCFs.output_vcf,
        vcf_index = GenotypeGVCFs.output_vcf_index,
        batch = batch,
        shard = "~{idx}",
        sv_pipeline_docker = sv_pipeline_docker,
        runtime_attr_override = runtime_attr_baf_gen,
    }
  }

  call GatherBAF {
    input:
      batch = batch,
      BAF = GenerateBAF.BAF,
      sv_mini_docker = sv_mini_docker,
      runtime_attr_override = runtime_attr_gather
  }

  scatter (sample in samples) {
    call ScatterBAFBySample {
      input:
        sample = sample,
        BAF = GatherBAF.out,
        sv_mini_docker = sv_mini_docker,
        runtime_attr_override = runtime_attr_gather
    }
  }

  output {
    Array[File] baf_files = ScatterBAFBySample.out
    Array[File] baf_file_indexes = ScatterBAFBySample.out_index
  }
}

task DynamicallyCombineIntervals {
  input {
    File intervals
    Int merge_count
    Int preemptible
  }

  command {
    python << CODE
    def parse_interval(interval):
        colon_split = interval.split(":")
        chromosome = colon_split[0]
        dash_split = colon_split[1].split("-")
        start = int(dash_split[0])
        end = int(dash_split[1])
        return chromosome, start, end

    def add_interval(chr, start, end):
        lines_to_write.append(chr + ":" + str(start) + "-" + str(end))
        return chr, start, end

    count = 0
    chain_count = ~{merge_count}
    l_chr, l_start, l_end = "", 0, 0
    lines_to_write = []
    with open("~{intervals}") as f:
        with open("out.intervals", "w") as f1:
            for line in f.readlines():
                # initialization
                if count == 0:
                    w_chr, w_start, w_end = parse_interval(line)
                    count = 1
                    continue
                # reached number to combine, so spit out and start over
                if count == chain_count:
                    l_char, l_start, l_end = add_interval(w_chr, w_start, w_end)
                    w_chr, w_start, w_end = parse_interval(line)
                    count = 1
                    continue

                c_chr, c_start, c_end = parse_interval(line)
                # if adjacent keep the chain going
                if c_chr == w_chr and c_start == w_end + 1:
                    w_end = c_end
                    count += 1
                    continue
                # not adjacent, end here and start a new chain
                else:
                    l_char, l_start, l_end = add_interval(w_chr, w_start, w_end)
                    w_chr, w_start, w_end = parse_interval(line)
                    count = 1
            if l_char != w_chr or l_start != w_start or l_end != w_end:
                add_interval(w_chr, w_start, w_end)
            f1.writelines("\n".join(lines_to_write))
    CODE
  }

  runtime {
    memory: "3 GB"
    preemptible: preemptible
    docker: "python:2.7"
    maxRetries: "1"
  }

  output {
    File output_intervals = "out.intervals"
  }
}

task GenotypeGVCFs {
  input {
    File workspace_tar
    String interval

    String output_vcf_filename

    String gatk_path

    File ref_fasta
    File ref_fasta_index
    File ref_dict

    String dbsnp_vcf
    String docker
    Int disk_size
    Int preemptible
  }

  command <<<
    set -e

    tar -xf ~{workspace_tar}
    WORKSPACE=$( basename ~{workspace_tar} .tar)

    ~{gatk_path} --java-options "-Xmx5g -Xms5g" \
     GenotypeGVCFs \
     -R ~{ref_fasta} \
     -O ~{output_vcf_filename} \
     -D ~{dbsnp_vcf} \
     -G StandardAnnotation \
     --only-output-calls-starting-in-intervals \
     --use-new-qual-calculator \
     -V gendb://$WORKSPACE \
     -L ~{interval}
  >>>
  runtime {
    docker: docker
    memory: "7 GB"
    cpu: "2"
    disks: "local-disk " + disk_size + " HDD"
    preemptible: preemptible
    maxRetries: "1"
  }
  output {
    File output_vcf = "~{output_vcf_filename}"
    File output_vcf_index = "~{output_vcf_filename}.tbi"
  }
}

task ImportGVCFs {
  input {
    Array[String] sample_names
    Array[File] input_gvcfs
    Array[File] input_gvcfs_indices
    String interval

    String workspace_dir_name

    String gatk_path
    String docker
    Int disk_size
    Int preemptible
    Int batch_size
  }
  parameter_meta {
    input_gvcfs: {
      localization_optional: true
    }
    input_gvcfs_indices: {
      localization_optional: true
    }
  }

  command <<<
    set -e
    set -o pipefail

    python << CODE
    gvcfs = ['~{sep="','" input_gvcfs}']
    sample_names = ['~{sep="','" sample_names}']

    if len(gvcfs)!= len(sample_names):
      exit(1)

    with open("inputs.list", "w") as fi:
      for i in range(len(gvcfs)):
        fi.write(sample_names[i] + "\t" + gvcfs[i] + "\n")

    CODE

    rm -rf ~{workspace_dir_name}

    # The memory setting here is very important and must be several GB lower
    # than the total memory allocated to the VM because this tool uses
    # a significant amount of non-heap memory for native libraries.
    # Also, testing has shown that the multithreaded reader initialization
    # does not scale well beyond 5 threads, so don't increase beyond that.
    ~{gatk_path} --java-options "-Xmx4g -Xms4g" \
    GenomicsDBImport \
    --genomicsdb-workspace-path ~{workspace_dir_name} \
    --batch-size ~{batch_size} \
    -L ~{interval} \
    --sample-name-map inputs.list \
    --reader-threads 5 \
    -ip 500

    tar -cf ~{workspace_dir_name}.tar ~{workspace_dir_name}

  >>>
  runtime {
    docker: docker
    memory: "7 GB"
    cpu: "2"
    disks: "local-disk " + disk_size + " HDD"
    preemptible: preemptible
    maxRetries: "1"
  }
  output {
    File output_genomicsdb = "~{workspace_dir_name}.tar"
  }
}

task GenerateBAF {
  input {
    File vcf
    File vcf_index
    String batch
    String shard
    String sv_pipeline_docker
    BAFRuntimeAttr? runtime_attr_override
  }

  BAFRuntimeAttr default_attr = object {
    cpu_cores: 1, 
    mem_gb: 3.75,
    disk_gb: 10,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  BAFRuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File BAF = "BAF.~{batch}.shard-~{shard}.txt"
  }
  command <<<

    set -euo pipefail
    bcftools view -M2 -v snps ~{vcf} \
      | python /opt/sv-pipeline/02_evidence_assessment/02d_baftest/scripts/Filegenerate/scratch.py --unfiltered
    mv mytest1.snp BAF.~{batch}.shard-~{shard}.txt
    
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

task GatherBAF {
  input {
    String batch
    Array[File] BAF
    String sv_mini_docker
    BAFRuntimeAttr? runtime_attr_override
  }

  BAFRuntimeAttr default_attr = object {
    cpu_cores: 1, 
    mem_gb: 3.75,
    disk_gb: 100,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  BAFRuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File out = "BAF.~{batch}.txt.gz"
    File out_index = "BAF.~{batch}.txt.gz.tbi"
  }
  command <<<

    set -euo pipefail
    cat ~{sep=" "  BAF} | bgzip -c > BAF.~{batch}.txt.gz
    tabix -f -s1 -b 2 -e 2 BAF.~{batch}.txt.gz

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

task ScatterBAFBySample {
  input {
    File BAF
    String sample
    String sv_mini_docker
    BAFRuntimeAttr? runtime_attr_override
  }

  Int vm_disk_size = ceil(size(BAF, "GB") + 10)

  BAFRuntimeAttr default_attr = object {
    cpu_cores: 1,
    mem_gb: 3.75,
    disk_gb: vm_disk_size,
    boot_disk_gb: 10,
    preemptible_tries: 3,
    max_retries: 1
  }
  BAFRuntimeAttr runtime_attr = select_first([runtime_attr_override, default_attr])

  output {
    File out = "BAF.~{sample}.txt.gz"
    File out_index = "BAF.~{sample}.txt.gz.tbi"
  }
  command <<<

    set -euo pipefail
    zcat ~{BAF} | fgrep -w "~{sample}" | bgzip -c > BAF.~{sample}.txt.gz
    tabix -s 1 -b 2 -e 2 BAF.~{sample}.txt.gz

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
