package com.saltdamage.service;

import com.saltdamage.dto.ChamberDTO;
import com.saltdamage.dto.TombDTO;
import com.saltdamage.entity.Chamber;
import com.saltdamage.entity.Tomb;
import com.saltdamage.repository.ChamberRepository;
import com.saltdamage.repository.TombRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TombService {

    private final TombRepository tombRepository;
    private final ChamberRepository chamberRepository;

    public List<TombDTO> getTombList(String status) {
        log.info("获取墓葬列表, status: {}", status);

        List<Tomb> tombs;
        if (status != null && !status.isEmpty()) {
            tombs = tombRepository.findByStatus(status);
        } else {
            tombs = tombRepository.findAll();
        }

        return tombs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public TombDTO getTombById(Long id) {
        log.info("获取墓葬详情, id: {}", id);
        return tombRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new IllegalArgumentException("墓葬不存在"));
    }

    public List<ChamberDTO> getChambersByTombId(Long tombId) {
        log.info("获取墓葬墓室列表, tombId: {}", tombId);

        if (!tombRepository.existsById(tombId)) {
            throw new IllegalArgumentException("墓葬不存在");
        }

        List<Chamber> chambers = chamberRepository.findByTombId(tombId);
        return chambers.stream()
                .map(this::convertToChamberDTO)
                .collect(Collectors.toList());
    }

    public ChamberDTO getChamberById(Long id) {
        log.info("获取墓室详情, id: {}", id);
        return chamberRepository.findById(id)
                .map(this::convertToChamberDTO)
                .orElseThrow(() -> new IllegalArgumentException("墓室不存在"));
    }

    private TombDTO convertToDTO(Tomb tomb) {
        TombDTO dto = new TombDTO();
        dto.setId(tomb.getId());
        dto.setName(tomb.getName());
        dto.setDescription(tomb.getDescription());
        dto.setLocation(tomb.getLocation());
        dto.setDynasty(tomb.getDynasty());
        dto.setBuiltYear(tomb.getBuiltYear());
        dto.setAreaSize(tomb.getAreaSize());
        dto.setImageUrl(tomb.getImageUrl());
        dto.setStatus(tomb.getStatus());
        dto.setCreateTime(tomb.getCreateTime());
        dto.setUpdateTime(tomb.getUpdateTime());

        if (tomb.getChambers() != null) {
            dto.setChambers(tomb.getChambers().stream()
                    .map(this::convertToChamberDTO)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private ChamberDTO convertToChamberDTO(Chamber chamber) {
        ChamberDTO dto = new ChamberDTO();
        dto.setId(chamber.getId());
        dto.setTombId(chamber.getTombId());
        dto.setName(chamber.getName());
        dto.setDescription(chamber.getDescription());
        dto.setLocation(chamber.getLocation());
        dto.setAreaSize(chamber.getAreaSize());
        dto.setWallMaterial(chamber.getWallMaterial());
        dto.setStatus(chamber.getStatus());
        dto.setCreateTime(chamber.getCreateTime());
        dto.setUpdateTime(chamber.getUpdateTime());
        return dto;
    }
}
