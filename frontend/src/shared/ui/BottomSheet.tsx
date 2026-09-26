import { Modal, type ModalProps } from './Modal';

export function BottomSheet(props: Omit<ModalProps, 'placement'>) {
  return <Modal {...props} placement="bottom" />;
}
